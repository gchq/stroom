package stroom.test.common;

/**
 * Static strings for use as {@link org.junit.jupiter.api.parallel.ResourceLock} keys
 * in tests. All string values must be unique.
 */
public class TestResourceLocks {

    private TestResourceLocks() {
    }

    /**
     * Resource lock on network port 8080 (Stroom's default app port).
     */
    public static final String STROOM_APP_PORT_8080 = "STROOM_APP_PORT_8080";

    /**
     * Resource lock on network port 8090 (Stroom-Proxy's default app port).
     */
    public static final String STROOM_PROXY_APP_PORT_8090 = "STROOM_PROXY_APP_PORT_8090";

}
