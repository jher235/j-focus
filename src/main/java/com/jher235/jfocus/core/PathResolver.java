package com.jher235.jfocus.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PathResolver {

    // Markers used to identify the root of a project
    private static final String[] MARKERS = {
        ".git",
        "settings.gradle", "settings.gradle.kts", // Multi-module root
        "build.gradle", "build.gradle.kts", "pom.xml" // Single-module root
    };

    /**
     * Finds the project root directory starting from the current working directory.
     * It uses the system property "user.dir" to determine where the command was executed.
     *
     * @return The path to the project root.
     */
    public Path findProjectRoot() {
        return findProjectRoot(
            Paths.get(System.getProperty("user.dir")).toAbsolutePath(),
            null
        );
    }

    /**
     * Recursive search for the project root by checking for marker files.
     *
     * @param startPath The path to start searching from.
     * @param ceiling The upper limit path to stop searching (can be null).
     * @return The detected project root path, or the startPath if not found.
     */
    Path findProjectRoot(Path startPath, Path ceiling) {
        Path path = startPath;

        while (path != null) {
            for (String marker : MARKERS) {
                if (Files.exists(path.resolve(marker))) {
                    return path;
                }
            }

            if (path.equals(ceiling)) {
                break;
            }
            path = path.getParent();
        }

        System.err.println(
            "Warning: Could not find project root (e.g., .git, build.gradle). Using current directory as fallback.");
        return startPath;
    }

    /**
     * Identifies the source code root (e.g., src/main/java) within the project.
     *
     * @param projectRoot The root directory of the project.
     * @return The path to the source directory.
     */
    public Path findSourceRoot(Path projectRoot) {
        // Standard structure for Maven/Gradle
        Path standardSrc = projectRoot.resolve("src/main/java");
        if (Files.exists(standardSrc)) {
            return standardSrc;
        }

        // Simple structure or non-standard layout
        // TODO: Add fallback logic for other project structures if needed
        return projectRoot;
    }
}