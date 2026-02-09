package com.jher235.jfocus.util;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import java.util.stream.Collectors;

public final class AstUtils {

    private AstUtils() {}

    /**
     * Generate a robust unique identifier for a method.
     * Format: FullyQualifiedClassName.MethodSignature
     */
    public static String createMethodId(MethodDeclaration md) {
        String fqcn = md.findAncestor(ClassOrInterfaceDeclaration.class)
            .flatMap(ClassOrInterfaceDeclaration::getFullyQualifiedName)
            .orElseGet(() -> {
                String className = md.findAncestor(ClassOrInterfaceDeclaration.class)
                    .map(ClassOrInterfaceDeclaration::getNameAsString)
                    .orElse("Unknown");
                String pkg = md.findCompilationUnit()
                    .flatMap(CompilationUnit::getPackageDeclaration)
                    .map(NodeWithName::getNameAsString)
                    .orElse("");
                return pkg.isEmpty() ? className : pkg + "." + className;
            });

        return fqcn + "." + md.getSignature().asString();
    }

    /**
     * Generate a robust unique identifier for a field declaration.
     * Format: FullyQualifiedClassName#field1,field2
     */
    public static String createFieldId(FieldDeclaration fd) {
        String className = fd.findAncestor(ClassOrInterfaceDeclaration.class)
            .map(c -> c.getFullyQualifiedName().orElse(c.getNameAsString()))
            .orElse("Unknown");

        String fieldNames = fd.getVariables().stream()
            .map(NodeWithSimpleName::getNameAsString)
            .collect(Collectors.joining(","));

        return className + "#" + fieldNames;
    }
}
