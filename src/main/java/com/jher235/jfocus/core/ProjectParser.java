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
     * return parsing AST(CompilationUnit) for file name (or path)
     */
    public Optional<CompilationUnit> parseFile(String fileName) {
        String targetName = fileName.endsWith(".java") ? fileName : fileName + ".java";

        try {
            try (Stream<Path> paths = Files.walk(sourceRoot)) {
                Optional<Path> targetFile = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(targetName))
                    .findFirst();
                if (targetFile.isPresent()) {
                    return javaParser.parse(targetFile.get()).getResult();
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: error while file searching " + e.getMessage());
        }

        System.err.println("Warning: cannot find file " + targetName);
        return Optional.empty();
    }
}