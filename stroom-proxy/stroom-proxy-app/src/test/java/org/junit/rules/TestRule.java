package org.junit.rules;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Stub to satisfy Testcontainers' dependency on JUnit 4 at compile time
 * without actually requiring JUnit 4.
 */
public interface TestRule {
    Statement apply(Statement base, Description description);
}
