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
     * Extracts the full context for the given target method recursively.
     *
     * @param targetMethod The method to analyze
     * @return Categorized context (target + internal + external + fields)
     */
    // Backward-compatible default: non-verbose (shallow)
    public ContextResult extractContext(MethodDeclaration targetMethod) {
        return extractContext(targetMethod, false);
    }

    /**
     * @param verbose if true, recursively traverses internal methods to collect
     *                their external calls as well (deep mode).
     *                if false, only collects direct (1-depth) calls of the target
     *                method (shallow mode).
     */
    public ContextResult extractContext(MethodDeclaration targetMethod, boolean verbose) {
        ContextResult result = new ContextResult(targetMethod);

        // Fields
        result.setUsedFields(dependencyResolver.resolveFields(targetMethod));

        // Track visited methods to prevent infinite loops during recursion
        Set<String> visited = new HashSet<>();
        visited.add(AstUtils.createMethodId(targetMethod));

        // shallow = !verbose: in shallow mode, stop recursing after the first level
        extractRecursive(targetMethod, targetMethod, result, visited, !verbose);

        return result;
    }

    /**
     * Traverses method calls to collect related user code.
     * External libraries are automatically excluded as they lack source code
     * definitions.
     *
     * @param shallow if true, stops after the first level of calls (no further
     *                recursion).
     *                if false, recursively follows all dependencies.
     */
    private void extractRecursive(MethodDeclaration rootTarget,
            MethodDeclaration currentMethod,
            ContextResult result,
            Set<String> visited,
            boolean shallow) {

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

            // In shallow mode, do not recurse further beyond the first level
            if (!shallow) {
                extractRecursive(rootTarget, dep, result, visited, shallow);
            }
        }
    }

}
