package com.jher235.jfocus.core;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.util.List;
import java.util.Optional;

public class MethodExtractor {

    /**
     * AST looks for all methods with a specific name (overloading correspondence)
     * @param cu parsed AST (CompilationUnit)
     * @param methodName Method name to find
     * @return Found method list (blank list if not)
     */
    public List<MethodDeclaration> extractMethods(CompilationUnit cu, String methodName) {
        return cu.findAll(MethodDeclaration.class).stream()
            .filter(method -> method.getNameAsString().equals(methodName))
            .toList();
    }

    /**
     * (for CLI output) Extracts the source code of the methods found into a string.
     * If there are multiple methods, combine them separated by lines.
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
