package stroom.state.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;

import java.util.UUID;
import java.util.function.BiConsumer;

public class ScyllaDbUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ScyllaDbUtil.class);

    public static final String DEFAULT_CONNECTION_YAML = """
            # See full reference https://github.com/apache/cassandra-java-driver/blob/4.0.1/core/src/main/resources/reference.conf
            datastax-java-driver {
                basic.contact-points = [ "127.0.0.1:9042" ]
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
    public static final String DEFAULT_KEYSPACE = "state";
    public static final String DEFAULT_KEYSPACE_CQL = createKeyspaceCql(DEFAULT_KEYSPACE);

//    public static CqlSession forTesting(final String keyspaceName) {
//        try (final CqlSession session = builder(DEFAULT_CONNECTION_YAML).build()) {
//            session.execute(createKeyspaceCql(keyspaceName));
//        }
//        return keyspace(DEFAULT_CONNECTION_YAML, keyspaceName);
//    }

    public static void test(final BiConsumer<CqlSession, String> consumer) {
        final String keyspaceName = "test" + UUID.randomUUID().toString().replaceAll("-", "");
        System.out.println("Using keyspace name: " + keyspaceName);
        try {
            try (final CqlSession session = builder(DEFAULT_CONNECTION_YAML).build()) {
                System.out.println("Creating keyspace: " + keyspaceName);
                session.execute(createKeyspaceCql(keyspaceName));
                System.out.println("Created keyspace: " + keyspaceName);
            }
            try (final CqlSession ks = keyspace(DEFAULT_CONNECTION_YAML, keyspaceName)) {
                consumer.accept(ks, keyspaceName);
            }
        } finally {
            try (final CqlSession session2 = builder(DEFAULT_CONNECTION_YAML).build()) {
                System.out.println("Dropping keyspace: " + keyspaceName);
                session2.execute(dropKeyspaceCql(keyspaceName));
                System.out.println("Dropped keyspace: " + keyspaceName);
            }
        }
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

//    public static void createKeyspace(final CqlSession session, final String keyspaceCql) {
//        session.execute(keyspaceCql);
//    }
//
//    public static void dropKeyspace(final CqlSession session, final String keyspaceCql) {
//        session.execute(keyspaceCql);
//    }

    public static void printMetadata(final CqlSession session, final String keyspaceName) {
        LOGGER.info("Print metadata...");
        LOGGER.logDurationIfInfoEnabled(() -> {
            final KeyspaceMetadata keyspace = session.getMetadata().getKeyspace(keyspaceName).orElseThrow();
            for (final TableMetadata table : keyspace.getTables().values()) {
                LOGGER.info("Keyspace: {}; Table: {}", keyspace.getName(), table.getName());
            }
        }, "printMetadata()");
    }
}
