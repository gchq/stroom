/*
 * Copyright 2016 Crown Copyright
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

package stroom.db.migration.mysql;

import stroom.util.logging.StroomLogger;
import org.flywaydb.core.api.migration.jdbc.JdbcMigration;

import java.sql.Connection;
import java.sql.Statement;

public class V5_0_0_1__CRT_MS implements JdbcMigration {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(V5_0_0_1__CRT_MS.class);

    private static final String[] TABLES = {"SYS_GRP", "SYS_ROLE", "SYS_PERM", "SYS_USR", "RK", "ND", "VOL", "DICT",
            "STRM_TP", "FD", "GLOB_PROP", "IDX", "IDX_SHRD", "JB", "JB_ND", "PIPE", "STRM_PROC", "STRM_ATR_KEY",
            "STRM_PROC_FILT", "TXT_CONV", "XML_SCHEMA", "XSLT", "DASH", "RES", "SCRIPT", "VIS", "QUERY", "STAT_DAT_SRC"};

    @Override
    public void migrate(Connection connection) throws Exception {
        // Update dashboard data.
        updateDashboardData(connection);
    }

    private void updateDashboardData(final Connection connection) throws Exception {
        for (final String table : TABLES) {
            migrate(connection, table, "CRT_DT", "CRT_MS");
            migrate(connection, table, "UPD_DT", "UPD_MS");
        }

        migrate(connection, "IDX_SHRD", "PART_FROM_DT", "PART_FROM_MS");
        migrate(connection, "IDX_SHRD", "PART_TO_DT", "PART_TO_MS");
    }

    private void migrate(final Connection connection, final String table, final String columnFrom, final String columnTo) throws Exception {
        // QUERY already has this column.
        if (!("QUERY".equals(table) && columnTo.equals("CRT_MS"))) {
            try (final Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + columnTo + " bigint(20) DEFAULT NULL;");
            }
            try (final Statement statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE " + table + " SET " + columnTo + " = (UNIX_TIMESTAMP(" + columnFrom + ") * 1000);");
            }
        }
        try (final Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + table + " DROP COLUMN " + columnFrom + ";");
        }
    }
}
