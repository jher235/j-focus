package com.jher235.jfocus.core;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
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
    private Path sourceRoot; // Mutable: can be updated based on package structure
    private JavaSymbolSolver symbolSolver;
    private JavaParser javaParser;

    public ProjectParser() {
        this.projectRoot = Paths.get(".").toAbsolutePath().normalize();

        // Initial guess: Prioritize src/main/java
        Path standardSourceRoot = projectRoot.resolve("src/main/java");
        if (Files.exists(standardSourceRoot)) {
            this.sourceRoot = standardSourceRoot;
            System.err.println("Source Root configured: " + this.sourceRoot);
        } else {
            this.sourceRoot = projectRoot;
            System.err.println("Warning: 'src/main/java' not found. Fallback to project root.");
        }

        initializeParser(this.sourceRoot);
    }

    private void initializeParser(Path root) {
        // Configure Parser to support modern Java features (Java 17+)
        ParserConfiguration config = new ParserConfiguration();
        config.setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);

        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());

        // Add source root solver if it's a directory
        if (Files.isDirectory(root)) {
            // Pass configuration to solver to ensure it parses dependencies correctly
            typeSolver.add(new JavaParserTypeSolver(root, config));
        }

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

            Path filePath = fileOpt.get();

            // Critical: Dynamically recalculate Source Root based on package declaration
            reconfigureSolverFromPackage(filePath);

            return parsePath(filePath);

        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Reads the package declaration from the file and aligns the Source Root.
     * e.g., if file is at .../src/foo/Bar.java and package is 'foo', root becomes .../src
     */
    private void reconfigureSolverFromPackage(Path filePath) {
        try {
            // Light parse to check package (without resolving symbols yet)
            CompilationUnit cu = javaParser.parse(filePath).getResult().orElse(null);
            if (cu == null) return;

            Path calculatedRoot = this.projectRoot;
            Optional<PackageDeclaration> pkgOpt = cu.getPackageDeclaration();

            if (pkgOpt.isPresent()) {
                String packageName = pkgOpt.get().getNameAsString();
                // Convert dots to path separators
                Path packagePath = Paths.get(packageName.replace('.', '/'));
                Path parentDir = filePath.getParent();

                // Check if the file path ends with the package structure
                if (parentDir != null && parentDir.endsWith(packagePath)) {
                    // Walk up the directory tree to find the root
                    int levelsUp = packagePath.getNameCount();
                    Path realRoot = parentDir;
                    for (int i = 0; i < levelsUp; i++) {
                        realRoot = realRoot.getParent();
                    }
                    calculatedRoot = realRoot;
                }
            } else {
                // Default package: The parent directory is the root
                calculatedRoot = filePath.getParent();
            }

            // Re-initialize solver only if the root has changed
            if (calculatedRoot != null && !calculatedRoot.equals(this.sourceRoot)) {
                System.err.println("Detected dynamic Source Root: " + calculatedRoot);
                this.sourceRoot = calculatedRoot;
                initializeParser(this.sourceRoot);
            }

        } catch (Exception e) {
            // Fallback to existing configuration on error
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

    private List<Path> scanForCandidates(String targetName) throws IOException {
        String baseName = getBaseName(targetName);
        String searchTerm = baseName.toLowerCase().replace(".java", "");

        // Scan projectRoot to cover multi-module or non-standard layouts
        // (Using projectRoot instead of sourceRoot ensures we find files even if initial guess was wrong)
        if (!Files.exists(projectRoot)) return Collections.emptyList();
        List<Path> matches = new ArrayList<>();

        Files.walkFileTree(projectRoot, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String dirName = dir.getFileName().toString();
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

    private Optional<Path> selectBestCandidate(List<Path> matches, String targetName, boolean allowInteractive) {
        if (matches.isEmpty()) return Optional.empty();

        String baseName = getBaseName(targetName);

        Optional<Path> exactMatch = matches.stream()
            .filter(p -> p.getFileName().toString().equalsIgnoreCase(baseName))
            .findFirst();

        if (exactMatch.isPresent()) {
            if (allowInteractive) System.out.println("Found exact match: " + exactMatch.get().getFileName());
            return exactMatch;
        }

        if (matches.size() == 1) {
            if (allowInteractive) System.out.println("Found file: " + matches.get(0).getFileName());
            return Optional.of(matches.get(0));
        }

        if (!allowInteractive) {
            return Optional.of(matches.get(0));
        }

        if (System.console() == null) {
            System.err.println("Ambiguous file name in non-interactive session.");
            return Optional.empty();
        }

        return promptUserForSelection(matches, targetName);
    }

    private Optional<Path> promptUserForSelection(List<Path> matches, String targetName) {
        System.out.println("Ambiguous file name. Found " + matches.size() + " matches:");

        for (int i = 0; i < matches.size(); i++) {
            Path path = matches.get(i);
            // Display path relative to project root for clarity
            String parentPath = projectRoot.relativize(path.getParent()).toString().replace("\\", "/");
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
