/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dataconnection.impl;

import stroom.cache.impl.CacheManagerImpl;
import stroom.credentials.api.StoredSecret;
import stroom.credentials.api.StoredSecrets;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.UsernamePasswordSecret;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.dataconnection.shared.JdbcConnectionSettings;
import stroom.util.cache.CacheConfig;
import stroom.util.io.PathCreator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestJdbcConnectionPoolCache {

    private static final String CREDENTIAL_NAME = "hr-read-only";

    // A PostgreSQL database for the tests in PostgreSql, which run only when these are set. The
    // account must be able to write, to show that the connection itself refuses writes.
    private static final String POSTGRES_URL_ENV = "STROOM_SQLDS_TEST_POSTGRES_URL";
    private static final String POSTGRES_USER_ENV = "STROOM_SQLDS_TEST_POSTGRES_USER";
    private static final String POSTGRES_PASSWORD_ENV = "STROOM_SQLDS_TEST_POSTGRES_PASSWORD";
    private static final String POSTGRES_CREDENTIAL_NAME = "postgres-writer";

    private final StoredSecrets storedSecrets = Mockito.mock(StoredSecrets.class);
    private FakeJdbcDriver driver;
    private CacheManagerImpl cacheManager;
    private JdbcConnectionPoolCache poolCache;

    @BeforeEach
    void setUp() throws SQLException {
        driver = FakeJdbcDriver.register();
        cacheManager = new CacheManagerImpl();
        final List<String> allowedUrlPatterns = new ArrayList<>(List.of(FakeJdbcDriver.ALLOWED_URL_PATTERN));
        if (System.getenv(POSTGRES_URL_ENV) != null) {
            allowedUrlPatterns.add("^" + Pattern.quote(System.getenv(POSTGRES_URL_ENV)) + "$");
            Mockito.when(storedSecrets.get(POSTGRES_CREDENTIAL_NAME)).thenReturn(new StoredSecret(
                    Mockito.mock(Credential.class),
                    new UsernamePasswordSecret(System.getenv(POSTGRES_USER_ENV),
                            System.getenv(POSTGRES_PASSWORD_ENV)),
                    null));
        }
        final DataConnectionConfig config = new DataConnectionConfig(
                allowedUrlPatterns, null, null, null, null, null, null, null, null, null, null, null, null);
        final JdbcConnectionResolver resolver = new JdbcConnectionResolver(() -> config, () -> storedSecrets);
        final JdbcDriverLoader driverLoader = new JdbcDriverLoader(() -> config, Mockito.mock(PathCreator.class));
        poolCache = new JdbcConnectionPoolCache(cacheManager, () -> config, resolver, driverLoader);

        Mockito.when(storedSecrets.get(CREDENTIAL_NAME)).thenReturn(new StoredSecret(
                Mockito.mock(Credential.class),
                new UsernamePasswordSecret("reader", "s3cret"),
                null));
    }

    @AfterEach
    void tearDown() throws SQLException {
        poolCache.clear();
        cacheManager.close();
        driver.deregister();
    }

    private static JdbcConnectionSettings settings() {
        return JdbcConnectionSettings.builder()
                .url(FakeJdbcDriver.URL)
                .credentialName(CREDENTIAL_NAME)
                .build();
    }

    @Test
    void lendsAReadOnlyConnectionAndReturnsTheResult() throws SQLException {
        final String result = poolCache.useConnection(settings(), connection -> "done");

        assertThat(result).isEqualTo("done");
        assertThat(driver.getConnections()).isNotEmpty();
        for (final Connection connection : driver.getConnections()) {
            Mockito.verify(connection).setReadOnly(true);
        }
    }

    @Test
    void connectsWithTheCredential() throws SQLException {
        poolCache.useConnection(settings(), connection -> null);

        assertThat(driver.getConnectionProperties())
                .isNotEmpty()
                .allSatisfy(properties -> {
                    assertThat(properties.getProperty("user")).isEqualTo("reader");
                    assertThat(properties.getProperty("password")).isEqualTo("s3cret");
                });
    }

    @Test
    void credentialIsResolvedOnEveryUse() throws SQLException {
        // This is where the current user's permission to use the credential is checked, so a pool
        // built for one user must not let another user in without the check.
        poolCache.useConnection(settings(), connection -> null);
        poolCache.useConnection(settings(), connection -> null);

        Mockito.verify(storedSecrets, Mockito.times(2)).get(CREDENTIAL_NAME);
    }

    @Test
    void sameSettingsShareOnePool() throws SQLException {
        final Connection[] firstConnection = new Connection[1];
        poolCache.useConnection(settings(), connection -> {
            firstConnection[0] = connection;
            return null;
        });
        final int connectionsOpened = driver.getConnections().size();

        poolCache.useConnection(settings(), connection -> null);

        assertThat(driver.getConnections())
                .as("the second use is served by the first pool's idle connection")
                .hasSize(connectionsOpened);
        assertThat(firstConnection[0]).isNotNull();
    }

    @Test
    void urlNotAllowedIsRefusedWithoutConnecting() {
        final JdbcConnectionSettings settings = JdbcConnectionSettings.builder()
                .url("jdbc:mysql://elsewhere:3306/hr")
                .build();

        assertThatThrownBy(() -> poolCache.useConnection(settings, connection -> null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(driver.getConnections()).isEmpty();
    }

    @Test
    void connectionsHaveAutoCommitOff() throws SQLException {
        // The fake connection reports auto-commit on, so the pool must turn it off.
        poolCache.useConnection(settings(), connection -> null);

        assertThat(driver.getConnections()).isNotEmpty();
        for (final Connection connection : driver.getConnections()) {
            Mockito.verify(connection).setAutoCommit(false);
        }
    }

    @Test
    void poolsLeftBehindByACacheRebuildAreClosed() throws SQLException {
        // A rebuild after a settings change swaps in a new cache without removing the old entries.
        final AtomicReference<CacheConfig> cacheConfig = new AtomicReference<>(CacheConfig.builder()
                .maximumSize(10L)
                .build());
        final DataConnectionConfig config = new DataConnectionConfig(
                List.of(FakeJdbcDriver.ALLOWED_URL_PATTERN), null, null, null, null, null, null, null, null,
                null, null, null, null);
        final CacheManagerImpl ownCacheManager = new CacheManagerImpl();
        final JdbcConnectionPoolCache rebuiltPoolCache = new JdbcConnectionPoolCache(
                ownCacheManager,
                () -> new DataConnectionConfig(
                        config.getAllowedUrlPatterns(), null, null, null, null, null, cacheConfig.get(), null,
                        null, null, null, null, null),
                new JdbcConnectionResolver(() -> config, () -> storedSecrets),
                new JdbcDriverLoader(() -> config, Mockito.mock(PathCreator.class)));
        try {
            rebuiltPoolCache.useConnection(settings(), connection -> null);
            final Connection firstPoolConnection = driver.getConnections().getLast();

            cacheConfig.set(CacheConfig.builder()
                    .maximumSize(20L)
                    .build());
            ownCacheManager.getLoadingCache("Data Connection JDBC Pool Cache").rebuild();
            rebuiltPoolCache.useConnection(settings(), connection -> null);

            Mockito.verify(firstPoolConnection).close();
        } finally {
            rebuiltPoolCache.clear();
            ownCacheManager.close();
        }
    }

    @Test
    void failureInTheFunctionStillReturnsTheConnection() throws SQLException {
        assertThatThrownBy(() -> poolCache.useConnection(settings(), connection -> {
            throw new SQLException("query failed");
        }))
                .isInstanceOf(SQLException.class)
                .hasMessage("query failed");

        // The pool is still usable, so the use was counted back in.
        final String result = poolCache.useConnection(settings(), connection -> "ok");
        assertThat(result).isEqualTo("ok");
    }


    // --------------------------------------------------------------------------------


    /// Checks against a real PostgreSQL database that connections refuse writes. PostgreSQL's driver
    /// only does so inside a transaction, so this fails if auto-commit is left on.
    @Nested
    @EnabledIfEnvironmentVariable(named = POSTGRES_URL_ENV, matches = ".+")
    class PostgreSql {

        private static final String TABLE = "sqlds_pool_test";

        private final JdbcConnectionSettings settings = JdbcConnectionSettings.builder()
                .url(System.getenv(POSTGRES_URL_ENV))
                .credentialName(POSTGRES_CREDENTIAL_NAME)
                .build();

        @BeforeEach
        void createTable() throws SQLException {
            try (final Connection connection = directConnection();
                    final Statement statement = connection.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS " + TABLE);
                statement.execute("CREATE TABLE " + TABLE + " (id INT, name TEXT)");
                statement.execute("INSERT INTO " + TABLE + " VALUES (1, 'Smith'), (2, 'Jones')");
            }
        }

        private Connection directConnection() throws SQLException {
            return DriverManager.getConnection(
                    System.getenv(POSTGRES_URL_ENV),
                    System.getenv(POSTGRES_USER_ENV),
                    System.getenv(POSTGRES_PASSWORD_ENV));
        }

        private int rowCount() throws SQLException {
            try (final Connection connection = directConnection();
                    final Statement statement = connection.createStatement();
                    final ResultSet resultSet = statement.executeQuery("SELECT count(*) FROM " + TABLE)) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }

        @Test
        void connectionIsReadOnlyWithAutoCommitOff() throws SQLException {
            final List<Boolean> state = poolCache.useConnection(settings, connection ->
                    List.of(connection.isReadOnly(), connection.getAutoCommit()));

            assertThat(state).containsExactly(true, false);
        }

        @Test
        void rowsCanBeRead() throws SQLException {
            final Integer count = poolCache.useConnection(settings, connection -> {
                try (final Statement statement = connection.createStatement();
                        final ResultSet resultSet = statement.executeQuery("SELECT count(*) FROM " + TABLE)) {
                    resultSet.next();
                    return resultSet.getInt(1);
                }
            });

            assertThat(count).isEqualTo(2);
        }

        @Test
        void sqlIsDescribedWithoutRunningIt() throws SQLException {
            // PostgreSQL's driver describes a prepared statement without running it.
            final JdbcQueryRunner runner = new JdbcQueryRunner(poolCache, DataConnectionConfig::new);
            final DataConnectionDoc doc = DataConnectionDoc.builder()
                    .uuid("uuid-1")
                    .jdbc(settings)
                    .build();
            final long start = System.nanoTime();

            final List<SqlColumn> columns = runner.describe(doc, SqlStatementParser.parse(
                    "select id, name, pg_sleep(5) as slept from " + TABLE + " where name = :name"));

            assertThat(columns).extracting(SqlColumn::name).containsExactly("id", "name", "slept");
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(4));
        }

        @Test
        void deleteHiddenInASelectIsRefused() throws SQLException {
            // executeQuery alone would let this through, as it returns rows.
            assertThatThrownBy(() -> poolCache.useConnection(settings, connection -> {
                try (final Statement statement = connection.createStatement();
                        final ResultSet resultSet = statement.executeQuery(
                                "WITH d AS (DELETE FROM " + TABLE + " RETURNING *) SELECT * FROM d")) {
                    return resultSet.next();
                }
            }))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("read-only transaction");

            assertThat(rowCount()).isEqualTo(2);
        }

        @Test
        void plainDeleteIsRefused() throws SQLException {
            assertThatThrownBy(() -> poolCache.useConnection(settings, connection -> {
                try (final Statement statement = connection.createStatement()) {
                    return statement.executeUpdate("DELETE FROM " + TABLE);
                }
            }))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("read-only transaction");

            assertThat(rowCount()).isEqualTo(2);
        }
    }
}
