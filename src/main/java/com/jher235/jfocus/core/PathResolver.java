package com.jher235.jfocus.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PathResolver {

    private static final String[] ROOT_MARKERS = {".git", "settings.gradle", "settings.gradle.kts"};
    private static final String[] BUILD_MARKERS = {"build.gradle", "build.gradle.kts", "pom.xml"};


    public Path findProjectRoot() {
        return findProjectRoot(Paths.get(System.getProperty("user.dir")).toAbsolutePath());
    }

    Path findProjectRoot(Path startPath) {

        Path result = walkUpAndFind(startPath, ROOT_MARKERS);
        if (result != null) {
            return result;
        }

        result = walkUpAndFind(startPath, BUILD_MARKERS);
        if (result != null) {
            return result;
        }

        System.err.println(
            "Warning: Could not find project root. Using current directory as fallback.");
        return startPath;
    }

    private Path walkUpAndFind(Path start, String[] markers) {
        Path path = start;
        while (path != null) {
            for (String marker : markers) {
                if (Files.exists(path.resolve(marker))) {
                    return path;
                }
            }
            path = path.getParent();
        }
        return null;
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