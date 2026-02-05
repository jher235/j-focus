package com.jher235.jfocus.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DependencyResolverTest {

    private DependencyResolver resolver;
    private MethodDeclaration targetMethod;

    @BeforeEach
    void setUp() {

        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);

        resolver = new DependencyResolver(symbolSolver);

        typeSolver.add(new ReflectionTypeSolver()); // JDK

        ParserConfiguration config = new ParserConfiguration();
        config.setSymbolResolver(new JavaSymbolSolver(typeSolver));
        JavaParser parser = new JavaParser(config);

        String sourceCode = """
            package com.test;
            
            import java.util.List;

            public class OrderService {
                
                private final PaymentService paymentService = new PaymentService();
                private int retryCount = 3;

                // Target Method
                public void processOrder(String orderId) {
                    // 1. 내부 필드 사용
                    System.out.println("Processing " + orderId + ", retry: " + retryCount);
                    
                    // 2. 내부 메서드 호출
                    validate(orderId);
                    
                    // 3. 외부 클래스 메서드 호출 (같은 파일 내 정의)
                    paymentService.pay(orderId);
                    
                    // 4. JDK 메서드 호출 (무시되어야 함)
                    List.of("A", "B");
                }

                private void validate(String id) {
                    if (id == null) throw new IllegalArgumentException();
                }
                
                // Inner class for simulation
                class PaymentService {
                    public void pay(String id) {
                        System.out.println("Paid");
                    }
                }
            }
            """;

        CompilationUnit cu = parser.parse(sourceCode).getResult().orElseThrow();
        
        // find target method
        targetMethod = cu.findFirst(MethodDeclaration.class, 
            m -> m.getNameAsString().equals("processOrder")).orElseThrow();
    }

    @Test
    @DisplayName("소스 코드 내의 메서드 호출만 추출해야 한다 (JDK 제외)")
    void shouldResolveSourceCodeMethodsOnly() {
        // when
        List<MethodDeclaration> dependencies = resolver.resolveMethods(targetMethod);

        // then
        assertThat(dependencies).hasSize(2); // validate(), pay()

        assertThat(dependencies)
            .anyMatch(m -> m.getNameAsString().equals("validate"));
        assertThat(dependencies)
            .anyMatch(m -> m.getNameAsString().equals("pay"));
            
        // excluded List.of
        assertThat(dependencies)
            .noneMatch(m -> m.getNameAsString().equals("of"));
    }

    @Test
    @DisplayName("소스 코드 내의 필드 사용만 추출해야 한다")
    void shouldResolveSourceCodeFieldsOnly() {
        // when
        List<FieldDeclaration> fields = resolver.resolveFields(targetMethod);

        // then
        assertThat(fields).hasSize(2); // retryCount, paymentService

        assertThat(fields)
            .anyMatch(f -> f.getVariable(0).getNameAsString().equals("retryCount"))
            .anyMatch(f -> f.getVariable(0).getNameAsString().equals("paymentService"));
    }
}