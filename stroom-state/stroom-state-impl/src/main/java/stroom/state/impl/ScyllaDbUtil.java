package stroom.state.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;

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
            }
            """;
    public static final String DEFAULT_KEYSPACE = "state";
    public static final String DEFAULT_KEYSPACE_CQL = """
            CREATE KEYSPACE IF NOT EXISTS state
            WITH replication = { 'class': 'NetworkTopologyStrategy', 'replication_factor': '1' }
            AND durable_writes = TRUE;
            """;

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

    public static void createKeyspace(final CqlSession session, final String keyspaceCql) {
        session.execute(keyspaceCql);
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
}
