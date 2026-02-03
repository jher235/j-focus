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
     * Supports both direct file paths and fuzzy search by filename.
     *
     * @param fileName The name or path of the file to parse (e.g., "OrderService" or "src/main/java/.../OrderService.java").
     * @return An Optional containing the parsed CompilationUnit if successful, or empty if not found.
     */
    public Optional<CompilationUnit> parseFile(String fileName) {
        String targetName = fileName.endsWith(".java") ? fileName : fileName + ".java";
        Path targetPath = Paths.get(targetName);

        try {
            Optional<CompilationUnit> result = Optional.empty();

            // 1. Try resolving as a direct path or relative to source root
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

            // 2. Fuzzy search: if only a filename is provided, search recursively in sourceRoot
            if (result.isEmpty() && targetPath.getNameCount() == 1) {
                try (Stream<Path> paths = Files.walk(sourceRoot)) {
                    List<Path> matches = paths
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().equals(targetName))
                        .limit(2)
                        .toList();

                    if (matches.size() == 1) {
                        result = javaParser.parse(matches.get(0)).getResult();
                    } else if (matches.size() > 1) {
                        System.err.println("Error: Ambiguous file name. Multiple files found for '" + targetName + "':");
                        matches.forEach(p -> System.err.println("   - " + sourceRoot.relativize(p)));
                        return Optional.empty();
                    }
                }
            }

            // Inject SymbolSolver manually to ensure symbols are resolvable in subsequent steps
            if (result.isPresent()) {
                CompilationUnit cu = result.get();
                cu.setData(Node.SYMBOL_RESOLVER_KEY, this.symbolSolver);
                return Optional.of(cu);
            }

        } catch (IOException e) {
            System.err.println("Warning: Error while searching file: " + e.getMessage());
        }

        System.err.println("Error: Cannot find file '" + targetName + "'");
        return Optional.empty();
    }
}