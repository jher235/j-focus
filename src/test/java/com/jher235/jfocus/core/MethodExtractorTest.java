package com.jher235.jfocus.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MethodExtractorTest {

    private MethodExtractor extractor;
    private CompilationUnit dummyAst;

    @BeforeEach
    void setUp() {
        extractor = new MethodExtractor();

        String dummyCode = """
            package com.test;
            
            public class UserService {
                private String name;
                
                // 1. target
                public void save(String user) {
                    System.out.println("Saving " + user);
                }
                
                // 2. overloading method (should found) 
                public void save(String user, boolean active) {
                    if (active) save(user);
                }

                // 3. should not found
                public void delete(int id) {
                    System.out.println("Deleting...");
                }
            }
            """;

        JavaParser parser = new JavaParser(new ParserConfiguration());
        dummyAst = parser.parse(dummyCode).getResult().orElseThrow();
    }

    @Test
    @DisplayName("이름이 일치하는 메서드를 정확히 추출해야 한다")
    void shouldExtractTargetMethod() {
        // when
        List<MethodDeclaration> methods = extractor.extractMethods(dummyAst, "delete");

        // then
        assertThat(methods).hasSize(1);
        assertThat(methods.get(0).getNameAsString()).isEqualTo("delete");
        assertThat(methods.get(0).toString()).contains("Deleting...");
    }

    @Test
    @DisplayName("오버로딩된 메서드가 있으면 모두 추출해야 한다")
    void shouldExtractOverloadedMethods() {
        // when
        List<MethodDeclaration> methods = extractor.extractMethods(dummyAst, "save");

        // then
        assertThat(methods).hasSize(2); // save(String) & save(String, boolean)

        assertThat(methods)
            .extracting(MethodDeclaration::toString)
            .anySatisfy(code -> {
                assertThat(code).contains("public void save(String user)");
                assertThat(code).contains("\"Saving \"");
                assertThat(code).contains("user");
            })
            .anySatisfy(code -> {
                assertThat(code).contains("public void save(String user, boolean active)");
                assertThat(code).contains("if (active)");
            });
    }

    @Test
    @DisplayName("존재하지 않는 메서드를 찾으면 빈 리스트를 반환해야 한다")
    void shouldReturnEmptyForUnknownMethod() {
        // when
        List<MethodDeclaration> methods = extractor.extractMethods(dummyAst, "update");

        // then
        assertThat(methods).isEmpty();
    }

    @Test
    @DisplayName("소스 코드 문자열 추출 시 오버로딩된 메서드가 합쳐져서 나와야 한다")
    void shouldExtractSourceCodeAsString() {
        // when
        Optional<String> source = extractor.extractMethodSource(dummyAst, "save");

        // then
        assertThat(source).isPresent();

        assertThat(source.get()).contains("public void save(String user)");
        assertThat(source.get()).contains("public void save(String user, boolean active)");
    }
}