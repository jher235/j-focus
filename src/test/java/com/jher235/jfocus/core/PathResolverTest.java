package com.jher235.jfocus.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PathResolverTest {

    @TempDir
    Path tempDir;

    PathResolver resolver;

    @BeforeEach
    public void setUp() throws IOException {
        resolver = new PathResolver();
    }

    @Test
    @DisplayName("루트 마커(.git)가 있는 디렉토리를 프로젝트 루트로 찾아야 한다")
    void shouldFindProjectRootWithGitMarker() throws IOException {
        // given
        Path projectRoot = tempDir.resolve("my-project");
        Files.createDirectories(projectRoot);
        Files.createFile(projectRoot.resolve(".git"));

        Path subDir = projectRoot.resolve("src/main/java");
        Files.createDirectories(subDir);

        // when
        Path result = resolver.findProjectRoot(subDir, tempDir);

        // then
        assertThat(result).isEqualTo(projectRoot);
    }

    @Test
    @DisplayName("빌드 마커(build.gradle)가 있는 디렉토리를 프로젝트 루트로 찾아야 한다")
    void shouldFindProjectRootWithBuildMarker() throws IOException {
        // given
        Path projectRoot = tempDir.resolve("gradle-project");
        Files.createDirectories(projectRoot);
        Files.createFile(projectRoot.resolve("build.gradle"));

        Path subDir = projectRoot.resolve("src");
        Files.createDirectories(subDir);

        // when
        Path result = resolver.findProjectRoot(subDir, tempDir);

        // then
        assertThat(result).isEqualTo(projectRoot);
    }

    @Test
    @DisplayName("마커가 없으면 시작 위치를 반환해야 한다 (Fallback)")
    void shouldReturnStartPathWhenNoMarkerFound() throws IOException {
        // given
        Path randomDir = tempDir.resolve("random/dir");
        Files.createDirectories(randomDir);

        // when
        Path result = resolver.findProjectRoot(randomDir, tempDir);

        // then
        assertThat(result).isEqualTo(randomDir);
    }

    @Test
    @DisplayName("src/main/java가 존재하면 소스 루트로 반환해야 한다")
    void shouldFindStandardSourceRoot() throws IOException {
        // given
        Path projectRoot = tempDir.resolve("project");
        Path srcMainJava = projectRoot.resolve("src/main/java");
        Files.createDirectories(srcMainJava);

        // when
        Path result = resolver.findSourceRoot(projectRoot);

        // then
        assertThat(result).isEqualTo(srcMainJava);
    }

    @Test
    @DisplayName("src/main/java가 없으면 프로젝트 루트를 반환해야 한다")
    void shouldReturnProjectRootWhenSourceRootNotFound() throws IOException {
        // given
        Path projectRoot = tempDir.resolve("project");
        Files.createDirectories(projectRoot);

        // when
        Path result = resolver.findSourceRoot(projectRoot);

        // then
        assertThat(result).isEqualTo(projectRoot);
    }
}