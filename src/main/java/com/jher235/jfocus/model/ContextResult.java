package com.jher235.jfocus.model;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds the categorized context result.
 */
public class ContextResult {
    private final MethodDeclaration targetMethod; // Target method (Layer 0)
    private final List<MethodDeclaration> internalMethods = new ArrayList<>(); // Internal methods (Layer 1: same class)
    private final List<MethodDeclaration> externalMethods = new ArrayList<>(); // External methods (Layer 2: different class)
    private final List<FieldDeclaration> usedFields = new ArrayList<>(); // Used fields

    public ContextResult(MethodDeclaration targetMethod) {
        this.targetMethod = targetMethod;
    }

    public MethodDeclaration getTargetMethod() { return targetMethod; }
    public List<MethodDeclaration> getInternalMethods() { return internalMethods; }
    public List<MethodDeclaration> getExternalMethods() { return externalMethods; }
    public List<FieldDeclaration> getUsedFields() { return usedFields; }

    public void setUsedFields(List<FieldDeclaration> fields) {
        this.usedFields.clear();
        this.usedFields.addAll(fields);
    }

    public void addInternalMethod(MethodDeclaration method) { this.internalMethods.add(method); }

    public void addExternalMethod(MethodDeclaration method) { this.externalMethods.add(method); }
}
