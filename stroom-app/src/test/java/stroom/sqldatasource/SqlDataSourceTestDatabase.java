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

package stroom.sqldatasource;

import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.credentials.shared.PutCredentialRequest;
import stroom.credentials.shared.UsernamePasswordSecret;
import stroom.sqldatasource.impl.SqlDataSourceConfig;
import stroom.test.common.util.db.DbTestUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.regex.Pattern;
import javax.sql.DataSource;

/// The test MySQL database as SQL Data Source tests use it: a small staff table and a database
/// account that can only read.
final class SqlDataSourceTestDatabase {

    static final String READER_USER = "sqlds_test_reader";
    static final String READER_PASSWORD = "sqlds_test_reader_pw";
    static final String TABLE = "sqlds_test_staff";
    static final String ALL_STAFF_SQL = "select id, name, joined from " + TABLE + " order by id";

    private SqlDataSourceTestDatabase() {
    }

    /// Creates the staff table in the test database this thread shares with Stroom, and the
    /// read-only account, as the documentation tells administrators to use.
    ///
    /// @return The JDBC URL of the test database.
    /// @throws SQLException If the database cannot be set up.
    static String prepare() throws SQLException {
        final DataSource dataSource = DbTestUtil.createTestDataSource();
        final String url;
        final String dbName;
        try (final Connection connection = dataSource.getConnection();
                final Statement statement = connection.createStatement()) {
            url = connection.getMetaData().getURL();
            dbName = connection.getCatalog();
            statement.execute("DROP TABLE IF EXISTS " + TABLE);
            statement.execute("CREATE TABLE " + TABLE + " (id INT PRIMARY KEY, name VARCHAR(50), joined DATETIME)");
            statement.execute("INSERT INTO " + TABLE + " VALUES " +
                              "(1, 'Smith', '2020-01-01 00:00:00'), " +
                              "(2, 'Jones', '2024-06-01 09:30:00'), " +
                              "(3, 'Brown', '2026-01-01 00:00:00')");
        }

        DbTestUtil.doWithRootConnection(connection -> {
            try (final Statement statement = connection.createStatement()) {
                statement.execute("CREATE USER IF NOT EXISTS '" + READER_USER + "'@'%' IDENTIFIED BY '"
                                  + READER_PASSWORD + "'");
                statement.execute("GRANT SELECT ON `" + dbName + "`.* TO '" + READER_USER + "'@'%'");
            } catch (final SQLException e) {
                throw new RuntimeException(e);
            }
        });
        return url;
    }

    /// @param uuid The credential's UUID.
    /// @param name The credential's name.
    /// @return A request to store the read-only account as a username and password credential.
    static PutCredentialRequest readerCredential(final String uuid, final String name) {
        return new PutCredentialRequest(
                new Credential(uuid,
                        name,
                        null,
                        null,
                        null,
                        null,
                        CredentialType.USERNAME_PASSWORD,
                        null,
                        null),
                new UsernamePasswordSecret(READER_USER, READER_PASSWORD));
    }

    /// @param config The configuration to change.
    /// @param url    The one JDBC URL to allow.
    /// @return The configuration, allowing only the URL.
    static SqlDataSourceConfig allowingOnly(final SqlDataSourceConfig config, final String url) {
        return new SqlDataSourceConfig(
                List.of("^" + Pattern.quote(url) + "$"),
                config.getDeniedConnectionProperties(),
                config.getDriverDirectory(),
                config.getMaxQueryTimeout(),
                config.getMaxRowsLimit(),
                config.getMaxPoolSizeLimit(),
                config.getConnectionPoolCache(),
                config.getFieldCache());
    }
}
