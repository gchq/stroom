package org.junit.runner;

import java.io.Serializable;

/**
 * Stub to satisfy Testcontainers' dependency on JUnit 4 at compile time
 * without actually requiring JUnit 4.
 */
public class Description implements Serializable {

    public static final Description EMPTY = new Description();

    private Description() {
    }
}
