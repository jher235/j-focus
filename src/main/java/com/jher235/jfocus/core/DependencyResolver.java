package com.jher235.jfocus.core;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
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

    /**
     * Finds ALL methods called by the target method that belong to the project source code.
     * This includes methods in other files (e.g., MyUtil.check()).
     *
     * @param targetMethod The method to analyze (Must be parsed with SymbolSolver configured)
     * @return List of method declarations found in the source code
     */
    public List<MethodDeclaration> resolveMethods(MethodDeclaration targetMethod) {
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
     */
    public List<FieldDeclaration> resolveFields(MethodDeclaration targetMethod) {
        List<FieldDeclaration> dependencies = new ArrayList<>();

        List<NameExpr> usedNames = targetMethod.findAll(NameExpr.class);

        for (NameExpr nameExpr : usedNames) {
            try {
                if (nameExpr.resolve() instanceof ResolvedFieldDeclaration resolvedField) {
                    if (resolvedField instanceof JavaParserFieldDeclaration) {
                        FieldDeclaration fieldNode = ((JavaParserFieldDeclaration) resolvedField).getWrappedNode();

                        if (!dependencies.contains(fieldNode)) {
                            dependencies.add(fieldNode);
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore local variables or unresolvable names
            }
        }

        return dependencies;
    }
}