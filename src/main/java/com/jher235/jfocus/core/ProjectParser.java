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
import java.util.Scanner;
import java.util.stream.Stream;

public class ProjectParser {

    private final Path sourceRoot;
    private final Path projectRoot;
    private final JavaSymbolSolver symbolSolver;
    private final JavaParser javaParser;

    public ProjectParser() {
        PathResolver pathResolver = new PathResolver();

        // Resolve project structure based on execution context
        this.projectRoot = pathResolver.findProjectRoot();
        this.sourceRoot = pathResolver.findSourceRoot(projectRoot);

        // Configure TypeSolver (Reflection + Source Code)
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());

        if (Files.isDirectory(sourceRoot)) {
            typeSolver.add(new JavaParserTypeSolver(sourceRoot));
        }

        this.symbolSolver = new JavaSymbolSolver(typeSolver);

        ParserConfiguration config = new ParserConfiguration();
        config.setSymbolResolver(symbolSolver);
        this.javaParser = new JavaParser(config);
    }

    /**
     * Parses a Java file into a CompilationUnit.
     * Strategies:
     * 1. Direct path resolution (Absolute/Relative).
     * 2. Fuzzy search in source root (Case-insensitive, Partial match).
     *
     * @param fileName The file path or name (e.g., "OrderService", "src/.../Order.java").
     * @return Optional containing the parsed unit, or empty if not found/ambiguous.
     */
    public Optional<CompilationUnit> parseFile(String fileName) {
        String targetName = normalizeFileName(fileName);

        try {
            // Strategy 1: Attempt direct resolution first
            Optional<CompilationUnit> directResult = tryParseDirectly(fileName, targetName);
            if (directResult.isPresent()) {
                return directResult;
            }

            // Strategy 2: Fuzzy search within the source directory
            List<Path> candidates = scanForCandidates(targetName);

            // Handle matches (Exact match vs Ambiguity)
            return selectBestCandidate(candidates, targetName)
                .map(this::parsePath)
                .orElse(Optional.empty());

        } catch (IOException e) {
            System.err.println("Warning: File resolution failed - " + e.getMessage());
            return Optional.empty();
        }
    }

    private String normalizeFileName(String fileName) {
        return fileName.toLowerCase().endsWith(".java")
            ? fileName
            : fileName + ".java";
    }

    /**
     * Attempts to resolve the file using direct paths (Absolute, Relative to Project/Source).
     */
    private Optional<CompilationUnit> tryParseDirectly(String originalInput, String targetName) throws IOException {
        Path directInput = Paths.get(originalInput);
        Path targetPath = Paths.get(targetName);

        // Priority A: Exact user input (Handles Case-Sensitive OS)
        if (Files.isRegularFile(directInput)) {
            return parsePath(directInput);
        }

        // Priority B: Normalized path
        if (Files.isRegularFile(targetPath)) {
            return parsePath(targetPath);
        }

        // Priority C: Relative to Project Root
        Path projectRelative = projectRoot.resolve(targetName);
        if (Files.isRegularFile(projectRelative)) {
            return parsePath(projectRelative);
        }

        // Priority D: Relative to Source Root
        Path sourceRelative = sourceRoot.resolve(targetName);
        if (Files.isRegularFile(sourceRelative)) {
            return parsePath(sourceRelative);
        }

        return Optional.empty();
    }

    /**
     * Scans the source directory recursively for potential matches.
     * Filters by extension, exact match, and partial match.
     */
    private List<Path> scanForCandidates(String targetName) throws IOException {
        String searchTerm = targetName.toLowerCase().replace(".java", "");

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> isMatch(path, targetName, searchTerm))
                .sorted((p1, p2) -> compareRelevance(p1, p2, searchTerm))
                .limit(10)
                .toList();
        }
    }

    private boolean isMatch(Path path, String targetName, String searchTerm) {
        String fileName = path.getFileName().toString();
        if (!fileName.toLowerCase().endsWith(".java")) {
            return false;
        }

        // Check A: Exact match (Case-Insensitive)
        if (fileName.equalsIgnoreCase(targetName)) {
            return true;
        }

        // Check B: Partial match
        String nameWithoutExt = fileName.substring(0, fileName.length() - 5);
        return nameWithoutExt.toLowerCase().contains(searchTerm);
    }

    private int compareRelevance(Path p1, Path p2, String searchTerm) {
        String n1 = p1.getFileName().toString().toLowerCase().replace(".java", "");
        String n2 = p2.getFileName().toString().toLowerCase().replace(".java", "");

        // 1. Exact match priority
        boolean exact1 = n1.equals(searchTerm);
        boolean exact2 = n2.equals(searchTerm);
        if (exact1 && !exact2) return -1;
        if (!exact1 && exact2) return 1;

        // 2. Starts with priority
        boolean start1 = n1.startsWith(searchTerm);
        boolean start2 = n2.startsWith(searchTerm);
        if (start1 && !start2) return -1;
        if (!start1 && start2) return 1;

        // 3. Length priority (Shortest first)
        int lenCompare = Integer.compare(n1.length(), n2.length());
        if (lenCompare != 0) return lenCompare;

        return n1.compareTo(n2);
    }

    /**
     * Resolves the best match from the candidate list.
     * Handles exact match prioritization and ambiguity reporting.
     */
    private Optional<Path> selectBestCandidate(List<Path> matches, String targetName) {
        if (matches.isEmpty()) {
            System.err.println("Error: No file found matching '" + targetName + "'");
            return Optional.empty();
        }

        // 1. Check for an exact name match within candidates
        Optional<Path> exactMatch = matches.stream()
            .filter(p -> p.getFileName().toString().equalsIgnoreCase(targetName))
            .findFirst();

        if (exactMatch.isPresent()) {
            System.out.println("Found exact match: " + exactMatch.get().getFileName());
            return exactMatch;
        }

        // 2. Single candidate found
        if (matches.size() == 1) {
            System.out.println("Found file: " + matches.get(0).getFileName());
            return Optional.of(matches.get(0));
        }

        // 3. Ambiguous results (Interactive Selection)
        // TODO: Implement interactive selection if needed
        System.err.println("Ambiguous file name. Found " + matches.size() + " matches:");
        for (int i = 0; i < matches.size(); i++) {
            System.out.println(String.format("   [%d] %s", i + 1, sourceRoot.relativize(matches.get(i))));
        }

        System.out.print("Select a file number (1-" + matches.size() + "): ");

        try {
            Scanner scanner = new Scanner(System.in);
            if (scanner.hasNextInt()) {
                int selection = scanner.nextInt();
                if (selection >= 1 && selection <= matches.size()) {
                    Path selectedPath = matches.get(selection - 1);
                    System.out.println("Selected: " + selectedPath.getFileName());
                    return Optional.of(selectedPath);
                } else {
                    System.err.println("Invalid selection number.");
                }
            } else {
                System.err.println("Invalid input. Please enter a number.");
            }
        } catch (Exception e) {
            System.err.println("Error reading input.");
        }

        return Optional.empty();
    }

    private Optional<CompilationUnit> parsePath(Path path) {
        try {
            CompilationUnit cu = javaParser.parse(path).getResult().orElseThrow();
            // Inject symbol solver for further analysis
            cu.setData(Node.SYMBOL_RESOLVER_KEY, this.symbolSolver);
            return Optional.of(cu);
        } catch (Exception e) {
            System.err.println("Failed to parse: " + path);
            return Optional.empty();
        }
    }

    private int prioritizeShortestName(Path p1, Path p2) {
        int len1 = p1.getFileName().toString().length();
        int len2 = p2.getFileName().toString().length();
        if (len1 != len2) return Integer.compare(len1, len2);
        return p1.compareTo(p2);
    }
}
