package com.jher235.jfocus.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.ast.CompilationUnit;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectParserTest {

    @Test
    @DisplayName("프로젝트 내의 존재하는 파일을 파싱하면 AST를 반환해야 한다")
    void shouldParseExistingFile() {
        // given
        ProjectParser parser = new ProjectParser();

        // when
        Optional<CompilationUnit> result = parser.parseFile("ProjectParser");

        // then
        assertThat(result).isPresent();

        String code = result.get().toString();
        assertThat(code).contains("class ProjectParser");
    }

    @Test
    @DisplayName("존재하지 않는 파일을 요청하면 빈 Optional을 반환해야 한다")
    void shouldReturnEmptyForNonExistingFile() {
        // given
        ProjectParser parser = new ProjectParser();

        // when
        Optional<CompilationUnit> result = parser.parseFile("GhostFile_Never_Exist");

        // then
        assertThat(result).isEmpty();
    }

}