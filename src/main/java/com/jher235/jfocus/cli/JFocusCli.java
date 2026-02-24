package com.jher235.jfocus.cli;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.jher235.jfocus.core.ContextExtractor;
import com.jher235.jfocus.core.MarkdownExporter;
import com.jher235.jfocus.core.MethodExtractor;
import com.jher235.jfocus.core.ProjectParser;
import com.jher235.jfocus.model.ContextResult;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "jfocus", mixinStandardHelpOptions = true, version = "jfocus 1.0.0", description = "Analyzes Java code context for LLM prompting.")
public class JFocusCli implements Callable<Integer> {

    private final Scanner scanner = new Scanner(System.in);
    @Parameters(index = "0", arity = "0..1", description = "Java file path or name (Interactive if empty)")
    private String fileName;
    @Parameters(index = "1", arity = "0..1", description = "Target method name (Interactive if empty)")
    private String methodName;
    @Option(names = { "-v", "--verbose" }, description = "Include full source code of external dependencies")
    private boolean verbose;
    @Option(names = { "-c", "--copy" }, description = "Copy to clipboard instead of stdout")
    private boolean copyToClipboard;
    @Option(names = { "-l", "--list" }, description = "List all available methods without interactive prompt")
    private boolean listMode;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new JFocusCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        try {
            // 1. Handle File Input (Interactive)
            while (fileName == null || fileName.isEmpty()) {
                // Use System.err for prompts to avoid polluting stdout during piping
                System.err.print("Enter file name to search: ");
                fileName = scanner.nextLine().trim();
                if (fileName.isEmpty()) {
                    System.err.println("File name cannot be empty.");
                }
            }

            System.err.println("Searching for: " + fileName + "...");

            // 2. Parse Project
            ProjectParser projectParser = new ProjectParser();
            Optional<CompilationUnit> cuOpt = projectParser.parseFile(fileName);

            if (cuOpt.isEmpty()) {
                return 1; // Error logged by ProjectParser
            }
            CompilationUnit cu = cuOpt.get();

            String foundPath = cu.getStorage().map(s -> s.getPath().toString()).orElse("Unknown");
            System.err.println("Found File: " + foundPath);

            if (listMode) {
                printMethodList(cu);
                return 0;
            }

            // 3. Handle Method Selection
            MethodDeclaration targetMethod;
            if (methodName == null) {
                targetMethod = selectMethodInteractive(cu);
            } else {
                MethodExtractor methodExtractor = new MethodExtractor();
                List<MethodDeclaration> methods = methodExtractor.extractMethods(cu, methodName);

                if (methods.isEmpty()) {
                    System.err.println("Error: Method '" + methodName + "' not found.");
                    return 1;
                }
                if (methods.size() > 1) {
                    boolean isInteractive = System.console() != null;
                    if (!isInteractive) {
                        System.err.println("[Error] Multiple overloads found for method '" + methodName + "'.");
                        System.err.println(
                                "In non-interactive mode, you MUST specify the exact signature wrapped in quotes.");
                        System.err.println("\nAvailable signatures:");
                        for (MethodDeclaration m : methods) {
                            System.err.println("- \"" + m.getDeclarationAsString(false, false, false) + "\"");
                        }
                        System.err.println("\nExiting. (Exit code 1)");
                        return 1;
                    }
                    System.err.println("Multiple overloads found. Please choose: ");
                    targetMethod = selectMethodInteractive(methods);
                } else {
                    targetMethod = methods.get(0);
                }
            }

            if (targetMethod == null)
                return 1;

            System.err.println("Analyzing method: " + targetMethod.getNameAsString() + "...");

            // 4. Extract & Export
            ContextExtractor contextExtractor = new ContextExtractor(projectParser);
            ContextResult contextResult = contextExtractor.extractContext(targetMethod, verbose);

            MarkdownExporter.ExportConfig config = new MarkdownExporter.ExportConfig(verbose);
            MarkdownExporter exporter = new MarkdownExporter(config);

            String report = exporter.export(contextResult);

            // 5. Output Handling
            if (copyToClipboard) {
                copyToClipboard(report);
            } else {
                System.out.println(report);
            }

            return 0;

        } catch (Exception e) {
            System.err.println("Critical Error: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    private MethodDeclaration selectMethodInteractive(CompilationUnit cu) {
        List<MethodDeclaration> allMethods = cu.findAll(MethodDeclaration.class);

        if (allMethods.isEmpty()) {
            System.err.println("No methods found in this file.");
            return null;
        }

        System.err.println("\nAvailable Methods:");
        for (int i = 0; i < allMethods.size(); i++) {
            MethodDeclaration m = allMethods.get(i);
            // Format: [1] methodName(paramType paramName)
            String params = m.getParameters().toString().replace("[", "(").replace("]", ")");
            System.err.printf(" [%d] %s%s\n", i + 1, m.getNameAsString(), params);
        }

        while (true) {
            System.err.print("\nSelect method number (or 'q' to quit): ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("q")) {
                return null;
            }

            try {
                int index = Integer.parseInt(input) - 1;
                if (index >= 0 && index < allMethods.size()) {
                    return allMethods.get(index);
                }
                System.err.println("Invalid number. Please try again.");
            } catch (NumberFormatException e) {
                System.err.println("Please enter a number.");
            }
        }
    }

    private MethodDeclaration selectMethodInteractive(List<MethodDeclaration> methods) {
        if (methods.isEmpty()) {
            System.err.println("No methods found in this file.");
            return null;
        }
        System.err.println("\nAvailable Methods:");
        for (int i = 0; i < methods.size(); i++) {
            MethodDeclaration m = methods.get(i);
            String params = m.getParameters().toString().replace("[", "(").replace("]", ")");
            System.err.printf(" [%d] %s%s\n", i + 1, m.getNameAsString(), params);
        }
        while (true) {
            System.err.print("\nSelect method number (or 'q' to quit): ");
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("q"))
                return null;
            try {
                int index = Integer.parseInt(input) - 1;
                if (index >= 0 && index < methods.size())
                    return methods.get(index);
                System.err.println("Invalid number. Please try again.");
            } catch (NumberFormatException e) {
                System.err.println("Please enter a number.");
            }
        }
    }

    private void copyToClipboard(String content) {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            StringSelection selection = new StringSelection(content);
            clipboard.setContents(selection, selection);

            System.err.println("Copied to clipboard! (Ready to paste)");

        } catch (java.awt.HeadlessException e) {
            // Fallback for headless environments (e.g., CI/CD, servers)
            System.err.println("Warning: Headless environment detected. Printing to stdout instead.");
            System.out.println(content);
        } catch (Exception e) {
            System.err.println("Copy failed. Printing result instead.");
            System.out.println(content);
        }
    }

    private void printMethodList(CompilationUnit cu) {
        List<MethodDeclaration> allMethods = cu.findAll(MethodDeclaration.class);

        if (allMethods.isEmpty()) {
            System.err.println("No methods found in this file.");
            return;
        }

        System.out.println("Available Methods:");
        for (MethodDeclaration m : allMethods) {
            String params = m.getParameters().toString().replace("[", "(").replace("]", ")");
            System.out.println("- " + m.getNameAsString() + params);
        }
    }
}
