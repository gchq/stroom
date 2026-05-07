package org.junit.runners.model;

/**
 * Stub to satisfy Testcontainers' dependency on JUnit 4 at compile time
 * without actually requiring JUnit 4.
 */
public abstract class Statement {
    public abstract void evaluate() throws Throwable;
}
