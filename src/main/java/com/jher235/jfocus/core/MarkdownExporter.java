package com.jher235.jfocus.core;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.jher235.jfocus.model.ContextResult;

/**
 * Converts the analyzed ContextResult into a formatted Markdown string.
 * This output is designed to be copy-pasted directly into LLM prompts.
 */
public class MarkdownExporter {

    // control external method body visibility
    private final boolean verbose;

    // default (signature only)
    public MarkdownExporter() {
        this(false);
    }

    public MarkdownExporter(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * NOTE: Currently, external methods exclude bodies to save tokens. We are
     * evaluating whether to provide full bodies for project-internal dependencies
     * in future iterations.
     */
    public String export(ContextResult result) {
        StringBuilder sb = new StringBuilder();

        sb.append("# Target Method\n");
        sb.append("The main logic to analyze.\n\n");
        appendCodeBlock(sb, result.getTargetMethod().toString());

        if (!result.getInternalMethods().isEmpty()) {
            sb.append("\n## Internal Context (Same Class)\n");
            sb.append("Methods called by the target, defined within the same class.\n\n");
            for (MethodDeclaration method : result.getInternalMethods()) {
                appendCodeBlock(sb, method.toString());
            }
        }

        if (!result.getExternalMethods().isEmpty()) {

            if(this.verbose){
                // Full Body
                sb.append("\n## External Context (Other Classes)\n");
                sb.append("Methods called by the target, defined in other classes (source code only).\n\n");
                for (MethodDeclaration method : result.getExternalMethods()) {
                    appendCodeBlock(sb, method.toString());
                }
            } else {
                // JavaDoc & Signature only
                sb.append("\n## External Context (Other Classes)\n");
                sb.append("Methods called by the target, defined in other classes.\n");
                sb.append("Signatures and JavaDocs are provided to maintain focus.\n\n");
                for (MethodDeclaration method : result.getExternalMethods()) {
                    method.getJavadoc().ifPresent(javadoc ->
                        sb.append(javadoc.toText()).append("\n"));

                    String signature = method.getDeclarationAsString(true, true, true) + ";";
                    appendCodeBlock(sb, signature);
                }
            }
        }

        if (!result.getUsedFields().isEmpty()) {
            sb.append("\n## Related Fields\n");
            sb.append("Class fields accessed by the target method.\n\n");
            for (FieldDeclaration field : result.getUsedFields()) {
                appendCodeBlock(sb, field.toString());
            }
        }

        return sb.toString();
    }

    private void appendCodeBlock(StringBuilder sb, String code) {
        sb.append("```java\n");
        sb.append(code).append("\n");
        sb.append("```\n\n");
    }
}