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

package stroom.db.util;

import stroom.config.common.CommonDbConfig;
import stroom.test.common.util.db.DbTestUtil;

import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestFlywayUtil {

    private static final String MODULE_NAME = "test-flyway-util";
    private static final String FLYWAY_TABLE_NAME = "test_flyway_util_schema_history";

    @TempDir
    private Path migrationsDir;

    private List<String> locations;
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws IOException {
        locations = List.of("filesystem:" + migrationsDir);
        // A new database for each test, so no test sees another's completed migrations
        dataSource = createDataSource();
        addMigration(1, "table_1");
    }

    @AfterEach
    void tearDown() {
        DbTestUtil.dropThreadTestDatabase(false);
    }

    @Test
    void testMigrate() throws SQLException {
        FlywayUtil.migrate(dataSource, locations, FLYWAY_TABLE_NAME, MODULE_NAME);

        assertThat(tableExists(dataSource, "table_1"))
                .isTrue();
    }

    @Test
    void testMigrate_sameAgain_isSkipped() throws IOException, SQLException {
        FlywayUtil.migrate(dataSource, locations, FLYWAY_TABLE_NAME, MODULE_NAME);
        // Only applied if Flyway runs again
        addMigration(2, "table_2");

        FlywayUtil.migrate(dataSource, locations, FLYWAY_TABLE_NAME, MODULE_NAME);

        assertThat(tableExists(dataSource, "table_2"))
                .isFalse();
    }

    @Test
    void testMigrate_newDataSource_isNotSkipped() throws IOException, SQLException {
        FlywayUtil.migrate(dataSource, locations, FLYWAY_TABLE_NAME, MODULE_NAME);
        addMigration(2, "table_2");
        // A different database, as when a test drops its database and creates a new one
        DbTestUtil.dropThreadTestDatabase(false);
        final DataSource newDataSource = createDataSource();

        FlywayUtil.migrate(newDataSource, locations, FLYWAY_TABLE_NAME, MODULE_NAME);

        assertThat(tableExists(newDataSource, "table_1"))
                .isTrue();
        assertThat(tableExists(newDataSource, "table_2"))
                .isTrue();
    }

    @Test
    void testMigrate_differentTarget_isNotSkipped() throws IOException, SQLException {
        addMigration(2, "table_2");
        FlywayUtil.migrate(dataSource, locations, MigrationVersion.fromVersion("1"), FLYWAY_TABLE_NAME, MODULE_NAME);
        assertThat(tableExists(dataSource, "table_2"))
                .isFalse();

        FlywayUtil.migrate(dataSource, locations, FLYWAY_TABLE_NAME, MODULE_NAME);

        assertThat(tableExists(dataSource, "table_2"))
                .isTrue();
    }

    @Test
    void testMigrate_differentHistoryTable_isNotSkipped() throws SQLException {
        FlywayUtil.migrate(dataSource, locations, FLYWAY_TABLE_NAME, MODULE_NAME);

        FlywayUtil.migrate(dataSource, locations, "other_schema_history", MODULE_NAME);

        assertThat(tableExists(dataSource, "other_schema_history"))
                .isTrue();
    }

    private static DataSource createDataSource() {
        // Not shared, so each call gets a new database
        return DbTestUtil.createTestDataSource(new CommonDbConfig(), MODULE_NAME, true, false);
    }

    private void addMigration(final int version, final String tableName) throws IOException {
        // IF NOT EXISTS so the same migrations can be run under a different history table
        Files.writeString(
                migrationsDir.resolve("V" + version + "__create_" + tableName + ".sql"),
                "CREATE TABLE IF NOT EXISTS " + tableName + " (id INT);");
    }

    private static boolean tableExists(final DataSource dataSource, final String tableName) throws SQLException {
        try (final Connection connection = dataSource.getConnection();
                final PreparedStatement statement = connection.prepareStatement("""
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = DATABASE()
                        AND table_name = ?""")) {
            statement.setString(1, tableName);
            try (final ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }
}
