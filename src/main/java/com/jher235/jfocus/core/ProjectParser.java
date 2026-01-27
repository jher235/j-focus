package com.jher235.jfocus.core;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class ProjectParser {

    private final Path sourceRoot;
    private final JavaParser javaParser;


    public ProjectParser() {
        PathResolver pathResolver = new PathResolver();
        Path projectRoot = pathResolver.findProjectRoot();
        this.sourceRoot = pathResolver.findSourceRoot(projectRoot);

        System.out.println("Project Root: " + projectRoot);
        System.out.println("Source Root: " + this.sourceRoot);

        this.javaParser = createConfiguredParser();
    }

    private JavaParser createConfiguredParser() {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(sourceRoot));

        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);

        ParserConfiguration config = new ParserConfiguration();
        config.setSymbolResolver(symbolSolver);

        return new JavaParser(config);
    }

    /**
     * Return parsing AST(CompilationUnit) for file name (or path).
     * When file duplicated, return error and induce specify path.
     */
    public Optional<CompilationUnit> parseFile(String fileName) {
        String targetName = fileName.endsWith(".java") ? fileName : fileName + ".java";

        try {
            Path directPath = sourceRoot.resolve(targetName);
            if (Files.isRegularFile(directPath)) {
                return javaParser.parse(directPath).getResult();
            }

            Path requestedPath = Path.of(targetName);
            if (requestedPath.getNameCount() > 1 || requestedPath.isAbsolute()) {
                System.err.println("Error: Cannot find file at path '" + targetName + "'");
                return Optional.empty();
            }

            try (Stream<Path> paths = Files.walk(sourceRoot)) {
                List<Path> matches = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(targetName))
                    .limit(2)
                    .toList();

                if (matches.size() == 1) {
                    return javaParser.parse(matches.get(0)).getResult();
                }

                if (matches.size() > 1) {
                    System.err.println(
                            "Error: Ambiguous file name. Multiple files found for '" + targetName + "':");
                    matches.forEach(p -> System.err.println("   - " + sourceRoot.relativize(p)));
                    System.err.println(
                        "Please specify the relative path (e.g., 'core/" + targetName + "')");
                    return Optional.empty();
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: error while searching file: " + e.getMessage());
        }

        System.err.println("Warning: cannot find file '" + targetName + "'");
        return Optional.empty();
    }
}