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
     * Handles unscoped calls (implicit this), explicit scopes (this., super.), and method overloading.
     */
    private Optional<MethodDeclaration> resolveByAstAnalysis(MethodDeclaration contextMethod, MethodCallExpr call) {
        String methodName = call.getNameAsString();
        int argCount = call.getArguments().size();

        // 1. Find the class containing the method
        ClassOrInterfaceDeclaration currentClass = contextMethod.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
        if (currentClass == null) return Optional.empty();

        // 2. Handle Unscoped Calls (e.g., internalMethod()) -> Implicit 'this'
        if (call.getScope().isEmpty()) {
            // 1. Check current class
            Optional<MethodDeclaration> local = currentClass.getMethodsByName(methodName).stream()
                .filter(m -> isArityMatch(m, argCount))
                .findFirst();
            if (local.isPresent()) return local;

            // 2. Check Superclass chain (Fallback for inherited methods)
            return findInSuperClass(currentClass, methodName, argCount);
        }

        String rawScope = call.getScope().get().toString(); // e.g., "user", "this.repository"
        String variableName = rawScope;

        boolean explicitThis = "this".equals(rawScope) || rawScope.startsWith("this.");
        boolean explicitSuper = "super".equals(rawScope) || rawScope.startsWith("super.");

        // 3. Normalize Scope (Strip this./super.)
        if (variableName.contains(".")) {
            if (explicitThis || explicitSuper) {
                variableName = variableName.substring(variableName.indexOf('.') + 1);
            } else {
                return Optional.empty(); // Ignore complex chains
            }
        }

        // 4. Resolve Variable Type
        String typeName = null;

        if (explicitThis) {
            // Case: this.method() -> Type is Current Class
            if ("this".equals(rawScope)) {
                typeName = currentClass.getNameAsString();
            }
            // Case: this.field.method() -> Find field strictly
            else {
                typeName = findFieldType(currentClass, variableName);
            }
        } else if (explicitSuper) {
            // Case: super.method() -> Type is Parent Class
            if ("super".equals(rawScope)) {
                typeName = currentClass.getExtendedTypes().stream()
                    .findFirst()
                    .map(t -> t.getNameAsString())
                    .orElse("Object");
            }
            // Case: super.field.method() -> Not supported in fallback (complex)
            else {
                return Optional.empty();
            }
        } else {
            // Case: variable.method() -> Priority: Local -> Param -> Field
            typeName = findLocalVariableType(contextMethod, variableName);
            if (typeName == null) typeName = findParameterType(contextMethod, variableName);
            if (typeName == null) typeName = findFieldType(currentClass, variableName);
        }

        if (typeName == null) return Optional.empty();

        // Strip generics
        if (typeName.contains("<")) {
            typeName = typeName.substring(0, typeName.indexOf("<")).trim();
        }

        if (JdkKnownTypes.contains(typeName)) return Optional.empty();

        // 5. Search for the file corresponding to the type name
        Optional<CompilationUnit> cuOpt = projectParser.findCompilationUnit(typeName);

        if (cuOpt.isPresent()) {
            CompilationUnit cu = cuOpt.get();
            injectSolver(cu);

            // 6. Find method with Overload Filtering (Arg Count Match)
            return cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .flatMap(c -> c.getMethodsByName(methodName).stream())
                .filter(m -> isArityMatch(m, argCount)) // [New] 오버로딩 필터링
                .findFirst();
        }

        return Optional.empty();
    }

    /**
     * Traverses the superclass chain to find a method by name and arity.
     */
    private Optional<MethodDeclaration> findInSuperClass(ClassOrInterfaceDeclaration currentClass, String methodName, int argCount) {
        String superType = currentClass.getExtendedTypes().stream()
            .findFirst()
            .map(t -> t.getNameAsString())
            .orElse(null);

        if (superType == null || JdkKnownTypes.contains(superType)) return Optional.empty();

        return projectParser.findCompilationUnit(superType).flatMap(cu -> {
            injectSolver(cu);
            return cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .flatMap(c -> c.getMethodsByName(methodName).stream())
                .filter(m -> isArityMatch(m, argCount))
                .findFirst();
        });
    }

    /**
     * Helper to check if method parameters match the argument count (handling varargs).
     */
    private boolean isArityMatch(MethodDeclaration method, int argCount) {
        int paramCount = method.getParameters().size();

        if (paramCount == argCount) return true;

        // Handle VarArgs (e.g., String... args)
        if (paramCount > 0 && method.getParameter(paramCount - 1).isVarArgs()) {
            // VarArgs allows argCount >= paramCount - 1
            return argCount >= (paramCount - 1);
        }

        return false;
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

    /**
     * Generate a robust unique identifier for the method.
     * Uses getFullyQualifiedName() to prevent collisions between nested classes.
     */
    private String getMethodId(MethodDeclaration md) {
        String fqcn = md.findAncestor(ClassOrInterfaceDeclaration.class)
            .flatMap(ClassOrInterfaceDeclaration::getFullyQualifiedName)
            .orElseGet(() -> {
                // Fallback for local classes or nodes not in a CU
                String className = md.findAncestor(ClassOrInterfaceDeclaration.class)
                    .map(ClassOrInterfaceDeclaration::getNameAsString)
                    .orElse("Unknown");
                String pkg = md.findCompilationUnit()
                    .flatMap(CompilationUnit::getPackageDeclaration)
                    .map(pd -> pd.getNameAsString())
                    .orElse("");
                return pkg.isEmpty() ? className : pkg + "." + className;
            });

        return fqcn + "." + md.getSignature().asString();
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
