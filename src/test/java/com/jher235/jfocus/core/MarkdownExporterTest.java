package com.jher235.jfocus.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.jher235.jfocus.model.ContextResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MarkdownExporterTest {

    private MarkdownExporter exporter;
    private ContextResult dummyResult;

    @BeforeEach
    void setUp() {
        exporter = new MarkdownExporter();

        JavaParser parser = new JavaParser(new ParserConfiguration());
        String code = """
            public class Demo {
                private int count;
                public void main() { helper(); }
                private void helper() { System.out.println("Help"); }
            }
            """;
        CompilationUnit cu = parser.parse(code).getResult().orElseThrow();

        MethodDeclaration target = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("main")).orElseThrow();
        MethodDeclaration internal = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("helper")).orElseThrow();

        dummyResult = new ContextResult(target);
        dummyResult.addInternalMethod(internal);
    }

    @Test
    @DisplayName("Should generate markdown with correct sections")
    void shouldExportMarkdown() {
        // when
        String markdown = exporter.export(dummyResult);

        // then
        assertThat(markdown).contains("# Target Method");
        assertThat(markdown).contains("## Internal Context");

        assertThat(markdown).doesNotContain("## External Context");

        assertThat(markdown).contains("public void main()");
        assertThat(markdown).contains("private void helper()");

        assertThat(markdown).contains("```java");
    }
}