package com.jher235.jfocus.core;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.resolution.Resolvable;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves dependencies using JavaParser's SymbolSolver.
 * Unlike simple name matching, this accurately distinguishes between
 * project source code (e.g., MyUtil) and external libraries (e.g., System.out).
 */
public class DependencyResolver {

    private final JavaSymbolSolver symbolSolver;

    public DependencyResolver(JavaSymbolSolver symbolSolver) {
        this.symbolSolver = symbolSolver;
    }

    /**
     * Finds ALL methods called by the target method that belong to the project source code.
     * This includes methods in other files (e.g., MyUtil.check()).
     *
     * @param targetMethod The method to analyze (Must be parsed with SymbolSolver configured)
     * @return List of method declarations found in the source code
     */
    public List<MethodDeclaration> resolveMethods(MethodDeclaration targetMethod) {
        injectSolver(targetMethod);

        List<MethodDeclaration> dependencies = new ArrayList<>();
        // Collect all method calls
        List<MethodCallExpr> methodCalls = targetMethod.findAll(MethodCallExpr.class);

        for (MethodCallExpr call : methodCalls) {
            try {
                ResolvedMethodDeclaration resolved = call.resolve();

                // JavaParserMethodDeclaration means it was parsed from a .java file in sourceRoot.
                // ReflectionMethodDeclaration means it's from JDK or .jar library.
                if (resolved instanceof JavaParserMethodDeclaration) {
                    MethodDeclaration methodNode = ((JavaParserMethodDeclaration) resolved).getWrappedNode();

                    // Inject symbol solver for further analysis
                    methodNode.findCompilationUnit().ifPresent(cu -> {
                        if (!cu.containsData(Node.SYMBOL_RESOLVER_KEY)) {
                            cu.setData(Node.SYMBOL_RESOLVER_KEY, this.symbolSolver);
                        }
                    });
                    injectSolver(targetMethod);

                    // Avoid self-recursion and duplicates
                    if (!methodNode.equals(targetMethod) && !dependencies.contains(methodNode)) {
                        dependencies.add(methodNode);
                    }
                }
            } catch (UnsolvedSymbolException e) {
                // pass
            } catch (RuntimeException e) {
                System.err.println("Warning: Failed to resolve '" + call + "': " + e.getMessage());
            }
        }
        return dependencies;
    }

    /**
     * Finds ALL fields used by the target method that belong to the project source code.
     * This handles inherited fields and fields from other classes if accessed directly.
     * NameExpr(Variable Name) + FieldAccessExpr(this.-)
     */
    public List<FieldDeclaration> resolveFields(MethodDeclaration targetMethod) {
        injectSolver(targetMethod);

        List<FieldDeclaration> dependencies = new ArrayList<>();

        // NameExpr
        targetMethod.findAll(NameExpr.class).forEach(expr ->
            resolveAndAddField(expr, dependencies));

        // FieldAccessExpr
        targetMethod.findAll(FieldAccessExpr.class).forEach(expr ->
            resolveAndAddField(expr, dependencies));

        return dependencies;
    }

    /**
     * Helper method that resolves an expression and adds it to the list if it is a field in our source code
     */
    private void resolveAndAddField(Resolvable<? extends ResolvedValueDeclaration> expr, List<FieldDeclaration> dependencies) {
        try {
            ResolvedValueDeclaration resolved = expr.resolve();

            if (resolved instanceof ResolvedFieldDeclaration resolvedField) {
                // client's source code
                if (resolvedField instanceof JavaParserFieldDeclaration) {
                    FieldDeclaration fieldNode = ((JavaParserFieldDeclaration) resolvedField).getWrappedNode();

                    if (!dependencies.contains(fieldNode)) {
                        dependencies.add(fieldNode);
                    }
                }
            }
        } catch (UnsolvedSymbolException e) {
            // pass
        } catch (RuntimeException e) {
            System.err.println("Warning: Failed to resolve field expression '" + expr + "': " + e.getMessage());
        }
    }

    /**
     * Helper to inject SymbolSolver into the CompilationUnit of a Node.
     * This ensures subsequent resolve() calls within that file succeed.
     */
    private void injectSolver(Node node) {
        node.findCompilationUnit().ifPresent(cu -> {
            if (!cu.containsData(Node.SYMBOL_RESOLVER_KEY)) {
                cu.setData(Node.SYMBOL_RESOLVER_KEY, this.symbolSolver);
            }
        });
    }
}