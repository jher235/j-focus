package com.jher235.jfocus.core;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.jher235.jfocus.model.ContextResult;

/**
 * Converts the analyzed ContextResult into a formatted Markdown string.
 * This output is designed to be copy-pasted directly into LLM prompts.
 */
public class MarkdownExporter {

    private final ExportConfig config;

    // Default constructor (uses default config)
    public MarkdownExporter() {
        this(ExportConfig.defaultConfig());
    }

    // Constructor with configuration object
    public MarkdownExporter(ExportConfig config) {
        this.config = config;
    }

    public String export(ContextResult result) {
        StringBuilder sb = new StringBuilder();

        // 1. Header & Target Method
        sb.append("# Target Method\n");
        sb.append("The main logic to analyze.\n\n");
        appendCodeBlock(sb, result.getTargetMethod().toString());

        // 2. Internal Context (Same Class)
        if (!result.getInternalMethods().isEmpty()) {
            sb.append("\n## Internal Context (Same Class)\n");
            sb.append("Methods called by the target, defined within the same class.\n\n");
            for (MethodDeclaration method : result.getInternalMethods()) {
                appendCodeBlock(sb, method.toString());
            }
        }

        // 3. External Context (Other Classes)
        // Logic extracted to a helper method to keep the main flow clean.
        if (!result.getExternalMethods().isEmpty()) {
            appendExternalContext(sb, result);
        }

        // 4. Related Fields
        if (!result.getUsedFields().isEmpty()) {
            sb.append("\n## Related Fields\n");
            sb.append("Class fields accessed by the target method.\n\n");
            for (FieldDeclaration field : result.getUsedFields()) {
                appendCodeBlock(sb, field.toString());
            }
        }

        return sb.toString();
    }

    /**
     * Appends external context information based on the configuration.
     * - Verbose: Includes full source code.
     * - Default: Includes only Signature and JavaDoc.
     */
    private void appendExternalContext(StringBuilder sb, ContextResult result) {
        sb.append("\n## External Context (Other Classes)\n");

        if (config.verbose()) {
            sb.append("Methods called by the target, defined in other classes.\n");
            sb.append("Full source code provided (--verbose).\n\n");

            for (MethodDeclaration method : result.getExternalMethods()) {
                appendCodeBlock(sb, method.toString());
            }
        } else {
            sb.append("Methods called by the target, defined in other classes.\n");
            sb.append("Signatures and JavaDocs are provided to maintain focus.\n\n");

            for (MethodDeclaration method : result.getExternalMethods()) {
                // Include JavaDoc if present as clean text
                method.getJavadoc().ifPresent(javadoc -> sb.append(javadoc.toText()).append("\n"));

                // Include Signature only
                String signature = method.getDeclarationAsString(true, true, true) + ";";
                appendCodeBlock(sb, signature);
            }
        }
    }

    private void appendCodeBlock(StringBuilder sb, String code) {
        sb.append("```java\n");
        sb.append(code).append("\n");
        sb.append("```\n\n");
    }

    /**
     * Configuration record for export options.
     * This avoids the "Boolean Trap" in constructors and allows for easy expansion
     * (e.g., maxDepth, includeFields, formatType) without breaking existing code.
     */
    public record ExportConfig(boolean verbose) {
        public static ExportConfig defaultConfig() {
            return new ExportConfig(false);
        }
    }
}