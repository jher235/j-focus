package com.jher235.jfocus.core;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.util.List;
import java.util.Optional;

public class MethodExtractor {

    /**
     * Finds all methods matching the specified name (case-insensitive).
     *
     * @param cu         The parsed CompilationUnit.
     * @param methodName The name of the method to find.
     * @return A list of matching MethodDeclarations.
     */
    public List<MethodDeclaration> extractMethods(CompilationUnit cu, String methodName) {
        return cu.findAll(MethodDeclaration.class).stream()
                .filter(method -> method.getNameAsString().equalsIgnoreCase(methodName) ||
                        method.getDeclarationAsString(false, false, true).equals(methodName))
                .toList();
    }

    /**
     * Extracts the source code of the found methods into a single string.
     *
     * @param cu         The parsed CompilationUnit.
     * @param methodName The name of the method to extract.
     * @return An Optional containing the combined source code of the methods.
     */
    public Optional<String> extractMethodSource(CompilationUnit cu, String methodName) {
        List<MethodDeclaration> methods = extractMethods(cu, methodName);
        if (methods.isEmpty()) {
            return Optional.empty();
        }

        StringBuilder sourceBuilder = new StringBuilder();
        for (MethodDeclaration method : methods) {
            sourceBuilder.append(method.toString()).append("\n\n");
        }

        return Optional.of(sourceBuilder.toString().trim());
    }
}