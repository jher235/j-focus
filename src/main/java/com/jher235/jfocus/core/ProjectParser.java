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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

public class ProjectParser {

    private final Path projectRoot;
    private final Path sourceRoot;
    private final JavaSymbolSolver symbolSolver;
    private final JavaParser javaParser;

    public ProjectParser() {
        this.projectRoot = Paths.get(".").toAbsolutePath().normalize();

        // Resolve source root (Prioritize src/main/java for correct package resolution)
        Path standardSourceRoot = projectRoot.resolve("src/main/java");
        if (Files.exists(standardSourceRoot)) {
            this.sourceRoot = standardSourceRoot;
            System.err.println("Source Root configured: " + this.sourceRoot);
        } else {
            this.sourceRoot = projectRoot;
            System.err.println("Warning: 'src/main/java' not found. Fallback to project root.");
        }

        // Configure Parser to support modern Java features (Java 17+)
        ParserConfiguration config = new ParserConfiguration();
        config.setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);

        // Configure TypeSolver
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());

        // Pass configuration to solver to ensure it parses dependencies correctly
        typeSolver.add(new JavaParserTypeSolver(sourceRoot, config));

        this.symbolSolver = new JavaSymbolSolver(typeSolver);
        config.setSymbolResolver(symbolSolver);
        this.javaParser = new JavaParser(config);
    }

    public JavaSymbolSolver getSymbolSolver() {
        return this.symbolSolver;
    }

    /**
     * Public API for internal use (DependencyResolver).
     * Searches silently without user interaction.
     */
    public Optional<CompilationUnit> findCompilationUnit(String className) {
        return parseFile(className, false);
    }

    /**
     * Public API for CLI interaction.
     * Allows interactive selection if ambiguities arise.
     */
    public Optional<CompilationUnit> parseFile(String fileName) {
        return parseFile(fileName, true);
    }

    private Optional<CompilationUnit> parseFile(String fileName, boolean allowInteractive) {
        String targetName = normalizeFileName(fileName);

        try {
            // Strategy 1: Attempt direct resolution first
            Optional<Path> fileOpt = resolveDirectPath(fileName, targetName);

            // Strategy 2: Fuzzy search within the source directory
            if (fileOpt.isEmpty()) {
                List<Path> candidates = scanForCandidates(targetName);
                fileOpt = selectBestCandidate(candidates, targetName, allowInteractive);
            }

            if (fileOpt.isEmpty()) {
                return Optional.empty();
            }

            return parsePath(fileOpt.get());

        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Optional<CompilationUnit> parsePath(Path path) {
        try {
            CompilationUnit cu = javaParser.parse(path).getResult().orElseThrow();
            // Inject symbol solver for further analysis
            cu.setData(Node.SYMBOL_RESOLVER_KEY, this.symbolSolver);
            return Optional.of(cu);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Attempts to resolve the file using direct paths (Absolute, Relative to Project/Source).
     */
    private Optional<Path> resolveDirectPath(String originalInput, String targetName) {
        try {
            Path directInput = Paths.get(originalInput);
            if (Files.isRegularFile(directInput)) return Optional.of(directInput);

            Path projectRelative = projectRoot.resolve(targetName);
            if (Files.isRegularFile(projectRelative)) return Optional.of(projectRelative);

            Path sourceRelative = sourceRoot.resolve(targetName);
            if (Files.isRegularFile(sourceRelative)) return Optional.of(sourceRelative);
        } catch (InvalidPathException e) {
            // Ignore invalid paths
        }
        return Optional.empty();
    }

    /**
     * Scans the source directory recursively for potential matches.
     * Optimized using walkFileTree to skip irrelevant directories.
     */
    private List<Path> scanForCandidates(String targetName) throws IOException {
        String baseName = getBaseName(targetName);
        String searchTerm = baseName.toLowerCase().replace(".java", "");

        if (!Files.exists(sourceRoot)) return Collections.emptyList();
        List<Path> matches = new ArrayList<>();

        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String dirName = dir.getFileName().toString();
                // Performance Optimization: Skip heavy/irrelevant directories
                if (dirName.startsWith(".") ||
                    dirName.equals("build") ||
                    dirName.equals("out") ||
                    dirName.equals("target") ||
                    dirName.equals("node_modules") ||
                    dirName.equals("gradle")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (isMatch(file, baseName, searchTerm)) {
                    matches.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return matches.stream()
            .sorted((p1, p2) -> compareRelevance(p1, p2, searchTerm))
            .limit(10)
            .toList();
    }

    /**
     * Resolves the best match from the candidate list.
     * Handles ambiguity based on interactivity mode.
     */
    private Optional<Path> selectBestCandidate(List<Path> matches, String targetName, boolean allowInteractive) {
        if (matches.isEmpty()) return Optional.empty();

        String baseName = getBaseName(targetName);

        // 1. Check for an exact name match
        Optional<Path> exactMatch = matches.stream()
            .filter(p -> p.getFileName().toString().equalsIgnoreCase(baseName))
            .findFirst();

        if (exactMatch.isPresent()) {
            if (allowInteractive) System.out.println("Found exact match: " + exactMatch.get().getFileName());
            return exactMatch;
        }

        // 2. Single candidate found
        if (matches.size() == 1) {
            if (allowInteractive) System.out.println("Found file: " + matches.get(0).getFileName());
            return Optional.of(matches.get(0));
        }

        // 3. Fallback for silent mode (Pick first or fail)
        if (!allowInteractive) {
            // In silent mode (dependency resolution), picking the first match is better than failing
            return Optional.of(matches.get(0));
        }

        // 4. Interactive selection
        if (System.console() == null) {
            System.err.println("Ambiguous file name in non-interactive session.");
            return Optional.empty();
        }

        return promptUserForSelection(matches, targetName);
    }

    private Optional<Path> promptUserForSelection(List<Path> matches, String targetName){
        System.out.println("Ambiguous file name. Found " + matches.size() + " matches:");

        for (int i = 0; i < matches.size(); i++) {
            Path path = matches.get(i);
            String parentPath = sourceRoot.relativize(path.getParent()).toString().replace("\\", "/");
            System.out.printf("   [%d] %-30s (%s)%n", i + 1, path.getFileName(), parentPath);
        }

        System.out.print("Select (1-" + matches.size() + "): ");

        try {
            Scanner scanner = new Scanner(System.in);
            if (scanner.hasNextInt()) {
                int selection = scanner.nextInt();
                if (selection >= 1 && selection <= matches.size()) {
                    return Optional.of(matches.get(selection - 1));
                }
            }
        } catch (Exception e) {
            // Ignore input errors
        }
        return Optional.empty();
    }

    private String normalizeFileName(String fileName) {
        if (fileName.toLowerCase().endsWith(".java")) {
            return fileName.substring(0, fileName.length() - 5) + ".java";
        }
        return fileName + ".java";
    }

    private String getBaseName(String path) {
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return (lastSlash >= 0) ? path.substring(lastSlash + 1) : path;
    }

    private boolean isMatch(Path path, String baseName, String searchTerm) {
        String fileName = path.getFileName().toString();
        if (!fileName.toLowerCase().endsWith(".java")) return false;
        if (fileName.equalsIgnoreCase(baseName)) return true;
        return fileName.toLowerCase().contains(searchTerm);
    }

    private int compareRelevance(Path p1, Path p2, String searchTerm) {
        String n1 = p1.getFileName().toString().toLowerCase().replace(".java", "");
        String n2 = p2.getFileName().toString().toLowerCase().replace(".java", "");

        if (n1.equals(searchTerm) && !n2.equals(searchTerm)) return -1;
        if (!n1.equals(searchTerm) && n2.equals(searchTerm)) return 1;

        int lenCompare = Integer.compare(n1.length(), n2.length());
        if (lenCompare != 0) return lenCompare;

        return n1.compareTo(n2);
    }
}
