package com.jher235.jfocus.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PathResolver {

    private static final String[] MARKERS = {
        ".git",
        "settings.gradle", "settings.gradle.kts", // multi module root
        "build.gradle", "build.gradle.kts", "pom.xml" // single module root
    };


    public Path findProjectRoot() {
        return findProjectRoot(
            Paths.get(System.getProperty("user.dir")).toAbsolutePath(),
            null
        );
    }

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
            "Warning: Could not find project root. Using current directory as fallback.");
        return startPath;
    }

    public Path findSourceRoot(Path projectRoot) {
        Path standardSrc = projectRoot.resolve("src/main/java");
        if (Files.exists(standardSrc)) {
            return standardSrc;
        }
        // TODO: Fallback for non-standard structures or multi-module projects
        return projectRoot;
    }
}