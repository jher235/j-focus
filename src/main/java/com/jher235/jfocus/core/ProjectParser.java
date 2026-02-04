package com.jher235.jfocus.core;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class ProjectParser {

    private final Path sourceRoot;
    private final Path projectRoot;
    private final JavaSymbolSolver symbolSolver;
    private final JavaParser javaParser;

    public ProjectParser() {
        PathResolver pathResolver = new PathResolver();
        this.projectRoot = pathResolver.findProjectRoot();
        this.sourceRoot = pathResolver.findSourceRoot(projectRoot);

        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(sourceRoot));

        this.symbolSolver = new JavaSymbolSolver(typeSolver);

        ParserConfiguration config = new ParserConfiguration();
        config.setSymbolResolver(symbolSolver);

        this.javaParser = new JavaParser(config);
    }

    /**
     * Parses a Java file into a CompilationUnit.
     * Supports direct paths, case-insensitive search, and partial matching.
     *
     * @param fileName The name or path of the file to parse (e.g., "OrderService", "order", or "src/.../OrderService.java").
     * @return An Optional containing the parsed CompilationUnit if successful, or empty if not found.
     */
    public Optional<CompilationUnit> parseFile(String fileName) {
        String rawName = fileName.endsWith(".java")
            ? fileName.substring(0, fileName.length() - 5)
            : fileName;
        String targetName = rawName + ".java";
        Path targetPath = Paths.get(targetName);
        String baseName = targetPath.getFileName().toString();
        String searchNameLower = baseName.substring(0, baseName.length() - 5).toLowerCase();

        try {
            Optional<CompilationUnit> result = Optional.empty();

            // 1. Try resolving as a direct path or relative path
            if (Files.isRegularFile(targetPath)) {
                result = javaParser.parse(targetPath).getResult();
            } else {
                Path projectRelative = projectRoot.resolve(targetName);
                if (Files.isRegularFile(projectRelative)){
                    result = javaParser.parse(projectRelative).getResult();
                } else {
                    Path directPath = sourceRoot.resolve(targetName);
                    if (Files.isRegularFile(directPath)) {
                        result = javaParser.parse(directPath).getResult();
                    }
                }
            }

            // 2. Fuzzy search: if not found by direct path, search recursively in sourceRoot
            if (result.isEmpty()) {
                try (Stream<Path> paths = Files.walk(sourceRoot)) {
                    List<Path> matches = paths
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            String fName = p.getFileName().toString();
                            String fNameLower = fName.toLowerCase();
                            if (!fNameLower.endsWith(".java")) return false;

                            // A. Exact match (case-insensitive)
                            if (fName.equalsIgnoreCase(targetName)) return true;

                            // B. Partial match (case-insensitive, comparing without extension)
                            String nameWithoutExt = fName.substring(0, fName.length() - 5);
                            return nameWithoutExt.toLowerCase().contains(searchNameLower);
                        })
                        // Sort by length (shorter is likely more accurate) then alphabetically
                        .sorted((p1, p2) -> {
                            int len1 = p1.getFileName().toString().length();
                            int len2 = p2.getFileName().toString().length();
                            if (len1 != len2) return Integer.compare(len1, len2);
                            return p1.compareTo(p2);
                        })
                        .limit(5)
                        .toList();

                    if (matches.size() == 1) {
                        System.out.println("Found file: " + matches.get(0).getFileName());
                        result = javaParser.parse(matches.get(0)).getResult();
                    } else if (matches.size() > 1) {
                        System.err.println("Error: Ambiguous file name. Found " + matches.size() + " matches for '" + rawName + "':");
                        matches.forEach(p -> System.err.println("   - " + sourceRoot.relativize(p)));
                        return Optional.empty();
                    }
                }
            }

            if (result.isPresent()) {
                CompilationUnit cu = result.get();
                cu.setData(Node.SYMBOL_RESOLVER_KEY, this.symbolSolver);
                return Optional.of(cu);
            }

        } catch (IOException e) {
            System.err.println("Warning: Error while searching file: " + e.getMessage());
            return Optional.empty();
        }

        System.err.println("Error: Cannot find file matching '" + fileName + "'");
        return Optional.empty();
    }
}