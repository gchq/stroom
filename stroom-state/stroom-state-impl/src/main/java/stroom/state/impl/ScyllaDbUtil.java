package stroom.state.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    public static void test(final BiConsumer<Provider<CqlSession>, String> consumer) {
        final String connectionYaml = getDefaultConnection();
        final String keyspaceName = createTestKeyspaceName();
        LOGGER.info(() -> "Using keyspace name: " + keyspaceName);
        try {
            try (final CqlSession session = builder(connectionYaml).build()) {
                createKeyspace(session, keyspaceName);
            }
            try (final CqlSession ks = keyspace(connectionYaml, keyspaceName)) {
                consumer.accept(() -> ks, keyspaceName);
            }
        } finally {
            dropKeyspaceFromDefault(keyspaceName);
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
                "\nWITH replication = { 'class': 'NetworkTopologyStrategy', 'replication_factor': '1' }" +
                "\nAND durable_writes = TRUE;";
    }

    public static Optional<String> extractKeyspaceNameFromCql(final String cql) {
        final String[] parts = splitKeyspaceCql(cql);
        if (parts.length == 3) {
            return Optional.of(parts[1]);
        }
        return Optional.empty();
    }

    public static String replaceKeyspaceNameInCql(final String cql, final String keyspaceName) {
        final String[] parts = splitKeyspaceCql(cql);
        if (parts.length == 3) {
            final String prefix = parts[0];
            final String suffix = parts[2];
            return prefix + keyspaceName + suffix;
        }
        return cql;
    }

    private static String[] splitKeyspaceCql(final String cql) {
        final String lower = cql.toLowerCase(Locale.ROOT);
        int start = lower.indexOf("keyspace");
        if (start != -1) {
            start += "keyspace".length();
            int index2 = lower.indexOf("exists");
            if (index2 != -1) {
                index2 += "exists".length();
            }
            if (index2 > start) {
                start = index2;
            }
            final char[] chars = cql.toCharArray();
            while (start < chars.length && (Character.isWhitespace(chars[start]) || chars[start] == '\"')) {
                start++;
            }
            int end = start;
            while (end < chars.length && !Character.isWhitespace(chars[end]) && chars[end] != '\"') {
                end++;
            }

            final String prefix = cql.substring(0, start);
            final String keyspace = cql.substring(start, end);
            final String suffix = cql.substring(end);
            return new String[] {prefix, keyspace, suffix};
        }
        return new String[] {cql};
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

    public static void printMetadata(final Provider<CqlSession> sessionProvider, final String keyspaceName) {
        LOGGER.info("Print metadata...");
        LOGGER.logDurationIfInfoEnabled(() -> {
            final KeyspaceMetadata keyspace = sessionProvider
                    .get()
                    .getMetadata()
                    .getKeyspace(keyspaceName)
                    .orElseThrow();
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

    public static List<String> getKeyspacesFromDefault() {
        try (final CqlSession session = ScyllaDbUtil.connect(ScyllaDbUtil.getDefaultConnection())) {
            return getKeyspaces(session);
        }
    }

    public static List<String> getKeyspaces(final CqlSession session) {
        final List<String> list = new ArrayList<>();
        final ResultSet resultSet = session.execute("DESC keyspaces");
        resultSet.forEach(row -> {
            final String keyspace = row.getString(0);
            // Ignore system keyspaces.
            if (keyspace != null && !keyspace.startsWith("system")) {
                LOGGER.info("Found keyspace: " + keyspace);
                list.add(keyspace);
            }
        });
        return list;
    }

    public static void createKeyspace(final CqlSession session, final String keyspace) {
        LOGGER.info(() -> "Creating keyspace: " + keyspace);
        final String cql = createKeyspaceCql(keyspace);
        session.execute(cql);
        LOGGER.info(() -> "Created keyspace: " + keyspace);
    }

    public static void dropAllKeyspacesFromDefault() {
        try (final CqlSession session = ScyllaDbUtil.connect(ScyllaDbUtil.getDefaultConnection())) {
            dropAllKeyspaces(session);
        }
    }

    public static void dropAllKeyspaces(final CqlSession session) {
        getKeyspaces(session).forEach(keyspace -> {
            dropKeyspace(session, keyspace);
        });
    }

    public static void dropKeyspaceFromDefault(final String keyspace) {
        try (final CqlSession session = ScyllaDbUtil.connect(ScyllaDbUtil.getDefaultConnection())) {
            dropKeyspace(session, keyspace);
        }
    }

    public static void dropKeyspace(final CqlSession session, final String keyspace) {
        LOGGER.info(() -> "Dropping keyspace: " + keyspace);
        final String cql = dropKeyspaceCql(keyspace);
        session.execute(cql);
        LOGGER.info(() -> "Dropped keyspace: " + keyspace);
    }
}
