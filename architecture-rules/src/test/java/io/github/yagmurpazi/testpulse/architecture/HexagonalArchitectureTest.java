package io.github.yagmurpazi.testpulse.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Verifies that the shared rules accept a compliant layout and reject each kind of violation. */
class HexagonalArchitectureTest {

    private static final String FIXTURES = "io.github.yagmurpazi.testpulse.architecture.fixtures";

    @Test
    void acceptsCompliantService() {
        assertThat(evaluate("valid", HexagonalArchitecture::rulesFor).hasViolation()).isFalse();
    }

    @Test
    void rejectsDomainDependingOnLibrary() {
        assertThat(evaluate("domainuseslibrary", HexagonalArchitecture::domainIsFrameworkFree).hasViolation())
                .isTrue();
    }

    @Test
    void rejectsApplicationDependingOnLibrary() {
        assertThat(evaluate("applicationuseslibrary", HexagonalArchitecture::applicationIsFrameworkFree)
                .hasViolation()).isTrue();
    }

    @Test
    void rejectsDomainDependingOnAdapter() {
        assertThat(evaluate("domainusesadapter", HexagonalArchitecture::layerDependencies).hasViolation())
                .isTrue();
    }

    @Test
    void rejectsAdaptersDependingOnEachOther() {
        assertThat(evaluate("coupledadapters", HexagonalArchitecture::adaptersAreIndependent).hasViolation())
                .isTrue();
    }

    private static EvaluationResult evaluate(String fixture, Function<String, ArchRule> ruleFactory) {
        String basePackage = FIXTURES + "." + fixture;
        JavaClasses classes = new ClassFileImporter().importPackages(basePackage);
        return ruleFactory.apply(basePackage).evaluate(classes);
    }
}
