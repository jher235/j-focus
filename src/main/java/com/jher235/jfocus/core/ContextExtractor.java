package com.jher235.jfocus.core;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.jher235.jfocus.model.ContextResult;
import java.util.List;

/**
 * Orchestrates the extraction of code context.
 * It uses DependencyResolver to find related nodes and categorizes them
 * into internal (same class) and external (other classes) layers.
 */
public class ContextExtractor {

    private final DependencyResolver dependencyResolver;

    public ContextExtractor() {
        this.dependencyResolver = new DependencyResolver();
    }

    /**
     * Extracts the full context for the given target method.
     *
     * @param targetMethod The method to analyze
     * @return Categorized context (target + internal + external + fields)
     */
    public ContextResult extractContext(MethodDeclaration targetMethod) {
        ContextResult result = new ContextResult(targetMethod);
        // Fields
        result.setUsedFields(dependencyResolver.resolveFields(targetMethod));
        // Methods (Layer 1 vs Layer 2)
        List<MethodDeclaration> allDependencies = dependencyResolver.resolveMethods(targetMethod);

        ClassOrInterfaceDeclaration targetClass = targetMethod.findAncestor(ClassOrInterfaceDeclaration.class)
            .orElse(null);

        for (MethodDeclaration dep : allDependencies) {
            ClassOrInterfaceDeclaration depClass = dep.findAncestor(ClassOrInterfaceDeclaration.class)
                .orElse(null);

            if (targetClass != null && targetClass.equals(depClass)) {
                result.addInternalMethod(dep);
            } else {
                result.addExternalMethod(dep);
            }
        }
        return result;
    }

}
