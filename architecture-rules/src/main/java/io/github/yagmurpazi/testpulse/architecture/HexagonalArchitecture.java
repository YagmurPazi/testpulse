package io.github.yagmurpazi.testpulse.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;

/**
 * Hexagonal (ports and adapters) rules shared by every TestPulse service.
 *
 * <p>Expected package layout under a service's base package:
 * <pre>
 * domain/                  business model and rules, plain Java only
 * application/port/in/     use case interfaces (driving ports)
 * application/port/out/    interfaces the application needs from the outside (driven ports)
 * application/service/     use case implementations
 * adapter/in/&lt;tech&gt;/       driving adapters, e.g. web, messaging
 * adapter/out/&lt;tech&gt;/      driven adapters, e.g. persistence, messaging
 * config/                  framework wiring
 * </pre>
 *
 * <p>Empty layers are allowed so the rules can be applied before a layer has any classes.
 */
public final class HexagonalArchitecture {

    /** Annotation-only packages the inner layers may use (null-safety annotations). */
    private static final String JSPECIFY = "org.jspecify.annotations..";

    private HexagonalArchitecture() {
    }

    /** All hexagonal rules for the service rooted at {@code basePackage}. */
    public static ArchRule rulesFor(String basePackage) {
        return CompositeArchRule.of(layerDependencies(basePackage))
                .and(domainIsFrameworkFree(basePackage))
                .and(applicationIsFrameworkFree(basePackage))
                .and(adaptersAreIndependent(basePackage));
    }

    /** Dependencies point inwards: adapters and config may use the core, never the other way round. */
    public static ArchRule layerDependencies(String basePackage) {
        return layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .withOptionalLayers(true)
                .layer("Domain").definedBy(basePackage + ".domain..")
                .layer("Application").definedBy(basePackage + ".application..")
                .layer("Adapters").definedBy(basePackage + ".adapter..")
                .layer("Config").definedBy(basePackage + ".config..")
                .whereLayer("Config").mayNotBeAccessedByAnyLayer()
                .whereLayer("Adapters").mayOnlyBeAccessedByLayers("Config")
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapters", "Config")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapters", "Config")
                .as("Hexagonal layers of " + basePackage + " depend only inwards");
    }

    /** The domain depends on nothing but itself and the JDK: no Spring, no JPA, no Kafka, no event contracts. */
    public static ArchRule domainIsFrameworkFree(String basePackage) {
        return classes().that().resideInAPackage(basePackage + ".domain..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(basePackage + ".domain..", "java..", JSPECIFY)
                .allowEmptyShould(true)
                .as("Domain of " + basePackage + " is framework free");
    }

    /** The application layer orchestrates the domain through ports and stays framework free as well. */
    public static ArchRule applicationIsFrameworkFree(String basePackage) {
        return classes().that().resideInAPackage(basePackage + ".application..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        basePackage + ".application..", basePackage + ".domain..", "java..", JSPECIFY)
                .allowEmptyShould(true)
                .as("Application layer of " + basePackage + " is framework free");
    }

    /** One adapter (e.g. web) must not call another (e.g. persistence); they meet only through ports. */
    public static ArchRule adaptersAreIndependent(String basePackage) {
        return slices().matching(basePackage + ".adapter.(*).(*)..")
                .should().notDependOnEachOther()
                .allowEmptyShould(true)
                .as("Adapters of " + basePackage + " do not depend on each other");
    }
}
