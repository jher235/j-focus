package com.jher235.jfocus.core;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.jher235.jfocus.model.ContextResult;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates the extraction of code context.
 * It uses DependencyResolver to find related nodes and categorizes them
 * into internal (same class) and external (other classes) layers.
 * Supports recursive analysis to capture the full call chain within the project.
 */
public class ContextExtractor {

    private final DependencyResolver dependencyResolver;

    public ContextExtractor() {
        this.dependencyResolver = new DependencyResolver();
    }

    /**
     * Extracts the full context for the given target method recursively.
     *
     * @param targetMethod The method to analyze
     * @return Categorized context (target + internal + external + fields)
     */
    public ContextResult extractContext(MethodDeclaration targetMethod) {
        ContextResult result = new ContextResult(targetMethod);

        // Fields
        result.setUsedFields(dependencyResolver.resolveFields(targetMethod));

        // Track visited methods to prevent infinite loops during recursion
        Set<String> visited = new HashSet<>();
        visited.add(getMethodId(targetMethod));

        // Start recursive analysis
        extractRecursive(targetMethod, targetMethod, result, visited);

        return result;
    }

    /**
     * Recursively traverses method calls to find all related user code.
     * External libraries are automatically excluded as they lack source code definitions.
     */
    private void extractRecursive(MethodDeclaration rootTarget,
        MethodDeclaration currentMethod,
        ContextResult result,
        Set<String> visited) {

        List<MethodDeclaration> dependencies = dependencyResolver.resolveMethods(currentMethod);

        ClassOrInterfaceDeclaration rootClass = rootTarget.findAncestor(ClassOrInterfaceDeclaration.class)
            .orElse(null);

        for (MethodDeclaration dep : dependencies) {
            String depId = getMethodId(dep);

            // Skip if already visited to avoid cyclic dependency loops
            if (visited.contains(depId)) {
                continue;
            }

            visited.add(depId);

            ClassOrInterfaceDeclaration depClass = dep.findAncestor(ClassOrInterfaceDeclaration.class)
                .orElse(null);

            // Categorize into Internal (same class) vs External (different class)
            if (rootClass != null && rootClass.equals(depClass)) {
                result.addInternalMethod(dep);
            } else {
                result.addExternalMethod(dep);
            }

            // Continue recursion to find deeper dependencies
            extractRecursive(rootTarget, dep, result, visited);
        }
    }

    private String getMethodId(MethodDeclaration md) {
        String className = md.findAncestor(ClassOrInterfaceDeclaration.class)
            .map(c -> c.getNameAsString())
            .orElse("Unknown");
        return className + "." + md.getSignature().asString();
    }
}
