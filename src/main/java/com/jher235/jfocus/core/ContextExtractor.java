package com.jher235.jfocus.core;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.jher235.jfocus.model.ContextResult;
import com.jher235.jfocus.util.AstUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates the extraction of code context.
 * It uses DependencyResolver to find related nodes and categorizes them
 * into internal (same class) and external (other classes) layers.
 * Supports recursive analysis to capture the full call chain within the
 * project.
 */
public class ContextExtractor {

    private final DependencyResolver dependencyResolver;

    public ContextExtractor(ProjectParser projectParser) {
        this.dependencyResolver = new DependencyResolver(projectParser);
    }

    /**
     * Extracts the shallow context for the given target method (direct calls only).
     * Equivalent to {@code extractContext(targetMethod, false)}.
     *
     * @param targetMethod The method to analyze
     * @return Categorized context (target + internal + external + fields)
     */
    public ContextResult extractContext(MethodDeclaration targetMethod) {
        return extractContext(targetMethod, false);
    }

    /**
     * Extracts the context for the given target method.
     *
     * @param targetMethod The method to analyze
     * @param verbose      if true, recursively traverses all dependencies (deep
     *                     mode);
     *                     if false, only collects direct (1-depth) calls (shallow
     *                     mode).
     * @return Categorized context (target + internal + external + fields)
     */
    public ContextResult extractContext(MethodDeclaration targetMethod, boolean verbose) {
        ContextResult result = new ContextResult(targetMethod);

        // Fields
        result.setUsedFields(dependencyResolver.resolveFields(targetMethod));

        // Track visited methods to prevent infinite loops during recursion
        Set<String> visited = new HashSet<>();
        visited.add(AstUtils.createMethodId(targetMethod));

        // Start recursive analysis based on verbose flag
        extractRecursive(targetMethod, targetMethod, result, visited, verbose);

        return result;
    }

    /**
     * Traverses method calls to collect related user code.
     * External libraries are automatically excluded as they lack source code
     * definitions.
     *
     * @param verbose if false, stops after the first level of calls (no further
     *                recursion).
     *                if true, recursively follows all dependencies.
     */
    private void extractRecursive(MethodDeclaration rootTarget,
            MethodDeclaration currentMethod,
            ContextResult result,
            Set<String> visited,
            boolean verbose) {

        List<MethodDeclaration> dependencies = dependencyResolver.resolveMethods(currentMethod);

        ClassOrInterfaceDeclaration rootClass = rootTarget.findAncestor(ClassOrInterfaceDeclaration.class)
                .orElse(null);

        for (MethodDeclaration dep : dependencies) {
            String depId = AstUtils.createMethodId(dep);

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

            // In verbose mode, recurse further to capture deeper dependencies
            if (verbose) {
                extractRecursive(rootTarget, dep, result, visited, verbose);
            }
        }
    }

}
