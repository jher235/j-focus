package com.jher235.jfocus.core;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.resolution.Resolvable;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration;
import com.jher235.jfocus.constant.JdkKnownTypes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class DependencyResolver {

    private final ProjectParser projectParser;

    public DependencyResolver(ProjectParser projectParser) {
        this.projectParser = projectParser;
    }

    public List<MethodDeclaration> resolveMethods(MethodDeclaration targetMethod) {
        injectSolver(targetMethod);

        List<MethodDeclaration> dependencies = new ArrayList<>();
        // Track unique method signatures to prevent duplicates from different AST contexts
        Set<String> seenSignatures = new HashSet<>();

        List<MethodCallExpr> methodCalls = targetMethod.findAll(MethodCallExpr.class);

        for (MethodCallExpr call : methodCalls) {
            try {
                // 1. Try Strict Resolution (SymbolSolver)
                ResolvedMethodDeclaration resolved = call.resolve();
                if (resolved instanceof JavaParserMethodDeclaration) {
                    MethodDeclaration methodNode = ((JavaParserMethodDeclaration) resolved).getWrappedNode();
                    addDependency(dependencies, seenSignatures, targetMethod, methodNode);
                }
            } catch (Exception e) {
                // 2. Fallback: AST-based Type Tracking
                // Handles cases where symbols (like Mono) are missing or strict resolution fails
                resolveByAstAnalysis(targetMethod, call).ifPresent(methodNode ->
                    addDependency(dependencies, seenSignatures, targetMethod, methodNode));
            }
        }
        return dependencies;
    }

    /**
     * Finds the method declaration by analyzing the AST structure.
     * Traces variable types from Fields, Parameters, and Local Variables.
     */
    private Optional<MethodDeclaration> resolveByAstAnalysis(MethodDeclaration contextMethod, MethodCallExpr call) {
        if (call.getScope().isEmpty()) return Optional.empty();

        String variableName = call.getScope().get().toString(); // e.g., "user", "userInfoFacade"
        String methodName = call.getNameAsString();             // e.g., "setName", "patchUserInfo"

        // Ignore complex chains or static calls (e.g., repository.findById(...))
        if (variableName.contains(".") || variableName.contains("(")) return Optional.empty();

        // 1. Find the class containing the method
        ClassOrInterfaceDeclaration currentClass = contextMethod.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
        if (currentClass == null) return Optional.empty();

        // 2. Resolve Variable Type (Priority: Local Var -> Parameter -> Field)
        String typeName = findLocalVariableType(contextMethod, variableName);

        if (typeName == null) {
            typeName = findParameterType(contextMethod, variableName);
        }
        if (typeName == null) {
            typeName = findFieldType(currentClass, variableName);
        }

        if (typeName == null) return Optional.empty();

        if (typeName.contains("<")) {
            typeName = typeName.substring(0, typeName.indexOf("<")).trim();
        }

        if (JdkKnownTypes.contains(typeName)) return Optional.empty();

        // 3. Search for the file corresponding to the type name
        Optional<CompilationUnit> cuOpt = projectParser.findCompilationUnit(typeName);

        if (cuOpt.isPresent()) {
            CompilationUnit cu = cuOpt.get();
            injectSolver(cu);

            // 4. Find the method within the identified file
            return cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .flatMap(c -> c.getMethodsByName(methodName).stream())
                .findFirst();
        }

        return Optional.empty();
    }

    /**
     * Scans for local variable declarations inside the method body.
     * e.g., User user = ...;
     */
    private String findLocalVariableType(MethodDeclaration method, String variableName) {
        return method.findAll(VariableDeclarator.class).stream()
            .filter(v -> v.getNameAsString().equals(variableName))
            .map(v -> v.getType().asString())
            .findFirst()
            .orElse(null);
    }

    private String findFieldType(ClassOrInterfaceDeclaration clazz, String variableName) {
        for (FieldDeclaration field : clazz.getFields()) {
            for (VariableDeclarator variable : field.getVariables()) {
                if (variable.getNameAsString().equals(variableName)) {
                    return variable.getType().asString();
                }
            }
        }
        return null;
    }

    private String findParameterType(MethodDeclaration method, String variableName) {
        return method.getParameters().stream()
            .filter(p -> p.getNameAsString().equals(variableName))
            .map(p -> p.getType().asString())
            .findFirst()
            .orElse(null);
    }

    private void addDependency(List<MethodDeclaration> dependencies, Set<String> seen, MethodDeclaration target, MethodDeclaration found) {
        injectSolver(found);

        String methodId = getMethodId(found);

        // Avoid self-reference and duplicates
        if (!found.equals(target) && !seen.contains(methodId)) {
            seen.add(methodId);
            dependencies.add(found);
        }
    }

    // Generate a unique identifier for the method: ClassName.MethodSignature
    private String getMethodId(MethodDeclaration md) {
        String className = md.findAncestor(ClassOrInterfaceDeclaration.class)
            .map(ClassOrInterfaceDeclaration::getNameAsString)
            .orElse("Unknown");
        return className + "." + md.getSignature().asString();
    }

    public List<FieldDeclaration> resolveFields(MethodDeclaration targetMethod) {
        injectSolver(targetMethod);
        List<FieldDeclaration> dependencies = new ArrayList<>();
        targetMethod.findAll(NameExpr.class).forEach(expr -> resolveAndAddField(expr, dependencies));
        targetMethod.findAll(FieldAccessExpr.class).forEach(expr -> resolveAndAddField(expr, dependencies));
        return dependencies;
    }

    private void resolveAndAddField(Resolvable<? extends ResolvedValueDeclaration> expr, List<FieldDeclaration> dependencies) {
        try {
            ResolvedValueDeclaration resolved = expr.resolve();
            if (resolved instanceof ResolvedFieldDeclaration resolvedField) {
                if (resolvedField instanceof JavaParserFieldDeclaration) {
                    FieldDeclaration fieldNode = ((JavaParserFieldDeclaration) resolvedField).getWrappedNode();
                    if (!dependencies.contains(fieldNode)) dependencies.add(fieldNode);
                }
            }
        } catch (Exception e) { /* Ignore */ }
    }

    private void injectSolver(Node node) {
        node.findCompilationUnit().ifPresent(cu -> {
            if (!cu.containsData(Node.SYMBOL_RESOLVER_KEY)) {
                cu.setData(Node.SYMBOL_RESOLVER_KEY, projectParser.getSymbolSolver());
            }
        });
    }
}
