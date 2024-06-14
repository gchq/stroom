package stroom.state.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ScyllaDbUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ScyllaDbUtil.class);

    private static final String DEFAULT_CONNECTION = """
            # See full reference https://github.com/apache/cassandra-java-driver/blob/4.0.1/core/src/main/resources/reference.conf
            datastax-java-driver {
                basic.contact-points = [ "localhost:9042" ]
                basic.session-name = my_session
                # basic.session-keyspace = state
                basic.load-balancing-policy {
                    local-datacenter = datacenter1
                }
                basic.request {
                    timeout = 10 seconds
                }
            }
            """;
    private static final String DEFAULT_KEYSPACE = "state";
    private static final String DEFAULT_KEYSPACE_CQL = createKeyspaceCql(DEFAULT_KEYSPACE);

    public static void test(final BiConsumer<CqlSession, String> consumer) {
        final String connectionYaml = getDefaultConnection();
        final String keyspaceName = createTestKeyspaceName();
        LOGGER.info(() -> "Using keyspace name: " + keyspaceName);
        try {
            try (final CqlSession session = builder(connectionYaml).build()) {
                LOGGER.info(() -> "Creating keyspace: " + keyspaceName);
                session.execute(createKeyspaceCql(keyspaceName));
                LOGGER.info(() -> "Created keyspace: " + keyspaceName);
            }
            try (final CqlSession ks = keyspace(connectionYaml, keyspaceName)) {
                consumer.accept(ks, keyspaceName);
            }
        } finally {
            try (final CqlSession session2 = builder(connectionYaml).build()) {
                LOGGER.info(() -> "Dropping keyspace: " + keyspaceName);
                session2.execute(dropKeyspaceCql(keyspaceName));
                LOGGER.info(() -> "Dropped keyspace: " + keyspaceName);
            }
        }
    }

    public static String createTestKeyspaceName() {
        return "test" + UUID.randomUUID().toString().replaceAll("-", "");
    }

    public static String getDefaultConnection() {
        // Allow the db conn details to be overridden with env vars, e.g. when we want to run tests
        // from within a container so we need a different host to localhost
        final String effectiveHost = getValueOrOverride(
                "STROOM_JDBC_DRIVER_HOST",
                "HOST IP",
                () -> "localhost",
                Function.identity());

        return DEFAULT_CONNECTION.replaceAll("localhost", effectiveHost);
    }

    public static String getDefaultKeyspace() {
        return DEFAULT_KEYSPACE;
    }

    public static String getDefaultKeyspaceCql() {
        return DEFAULT_KEYSPACE_CQL;
    }

    public static String createKeyspaceCql(final String keyspaceName) {
        return "CREATE KEYSPACE IF NOT EXISTS " +
                keyspaceName +
                " WITH replication = { 'class': 'NetworkTopologyStrategy', 'replication_factor': '1' }" +
                " AND durable_writes = TRUE;";
    }

    public static String dropKeyspaceCql(final String keyspaceName) {
        return "DROP KEYSPACE IF EXISTS " +
                keyspaceName +
                ";";
    }

    /**
     * Initiates a connection to the session.
     */
    public static CqlSession connect(final String connectionYaml) {
        return builder(connectionYaml).build();
    }

    public static CqlSession keyspace(final String connectionYaml, final String keyspaceName) {
        return builder(connectionYaml).withKeyspace(keyspaceName).build();
    }

    public static CqlSessionBuilder builder(final String connectionYaml) {
        return CqlSession.builder().withConfigLoader(DriverConfigLoader.fromString(connectionYaml));
    }

    public static void printMetadata(final CqlSession session, final String keyspaceName) {
        LOGGER.info("Print metadata...");
        LOGGER.logDurationIfInfoEnabled(() -> {
            final KeyspaceMetadata keyspace = session.getMetadata().getKeyspace(keyspaceName).orElseThrow();
            for (final TableMetadata table : keyspace.getTables().values()) {
                LOGGER.info("Keyspace: {}; Table: {}", keyspace.getName(), table.getName());
            }
        }, "printMetadata()");
    }

    private static <T> T getValueOrOverride(final String envVarName,
                                            final String propName,
                                            final Supplier<T> valueSupplier,
                                            final Function<String, T> typeMapper) {
        return Optional.ofNullable(System.getenv(envVarName))
                .map(envVarVal -> {
                    LOGGER.info("Overriding prop {} with value [{}] from {}",
                            propName,
                            envVarVal,
                            envVarName);
                    return typeMapper.apply(envVarVal);
                })
                .orElseGet(valueSupplier);
    }
}
