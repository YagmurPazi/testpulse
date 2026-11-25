package io.github.yagmurpazi.testpulse.analysis.domain;

import java.util.Objects;

/**
 * Natural identity of a test across runs. Each variant of a parameterised test is its own test; a renamed test is a
 * new test.
 *
 * @param repository repository in {@code owner/name} form
 * @param className  fully qualified test class name
 * @param testName   test method or display name
 */
public record TestKey(String repository, String className, String testName) {

    public TestKey {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(testName, "testName");
        if (repository.isBlank() || className.isBlank() || testName.isBlank()) {
            throw new IllegalArgumentException("repository, className and testName must not be blank");
        }
    }

    /** Short form for messages, e.g. {@code CheckoutServiceTest.appliesDiscount}. */
    public String displayName() {
        return className.substring(className.lastIndexOf('.') + 1) + "." + testName;
    }
}
