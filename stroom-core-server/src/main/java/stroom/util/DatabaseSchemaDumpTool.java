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

package stroom.util;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class DatabaseSchemaDumpTool extends AbstractCommandLineTool {
    private String jdbcDriverClassName;
    private String jdbcDriverUrl;
    private String jdbcDriverUsername;
    private String jdbcDriverPassword;

    @Override
    public void run() {
        try {
            Class.forName(jdbcDriverClassName);
            try (Connection connection = DriverManager.getConnection(jdbcDriverUrl, jdbcDriverUsername,
                    jdbcDriverPassword)) {
                final List<String> tableColumns = buildTableColumns(connection);
                tableColumns.forEach(System.out::println);
                connection.close();
            }
        } catch (final Exception ex) {
            ex.printStackTrace();
        }
    }

    private List<String> buildTableColumns(final Connection connection) throws SQLException {
        final List<String> rtnList = new ArrayList<>();
        final DatabaseMetaData databaseMetaData = connection.getMetaData();

        final HashSet<String> tables = new HashSet<>();
        String cat = null;
        String schema = null;

        try (final ResultSet tableRs = databaseMetaData.getTables(null, null, null, new String[]{"TABLE"})) {
            while (tableRs.next()) {
                tables.add(tableRs.getString("TABLE_NAME"));
                cat = tableRs.getString("TABLE_CAT");
                schema = tableRs.getString("TABLE_SCHEM");
            }
        }

        for (final String table : tables) {
            try (final ResultSet columnRs = databaseMetaData.getColumns(cat, schema, table, null)) {
                while (columnRs.next()) {
                    rtnList.add((table + " COL " + columnRs.getString("COLUMN_NAME") + "(" + columnRs.getString("TYPE_NAME")
                            + ")").toUpperCase());
                }
            }
            try (final ResultSet indexRs = databaseMetaData.getIndexInfo(cat, schema, table.toUpperCase(), true, false)) {
                while (indexRs.next()) {
                    final String idx = (table + " IDX " + indexRs.getString("INDEX_NAME")).toUpperCase();
                    if (!rtnList.contains(idx)) {
                        rtnList.add(idx);
                    }
                }
            }
        }

        Collections.sort(rtnList);

        return rtnList;

    }

    public static void main(final String[] args) throws Exception {
        new DatabaseSchemaDumpTool().doMain(args);
    }

    public String getJdbcDriverClassName() {
        return jdbcDriverClassName;
    }

    public void setJdbcDriverClassName(final String jdbcDriverClassName) {
        this.jdbcDriverClassName = jdbcDriverClassName;
    }

    public String getJdbcDriverUrl() {
        return jdbcDriverUrl;
    }

    public void setJdbcDriverUrl(final String jdbcDriverUrl) {
        this.jdbcDriverUrl = jdbcDriverUrl;
    }

    public String getJdbcDriverUsername() {
        return jdbcDriverUsername;
    }

    public void setJdbcDriverUsername(final String jdbcDriverUsername) {
        this.jdbcDriverUsername = jdbcDriverUsername;
    }

    public String getJdbcDriverPassword() {
        return jdbcDriverPassword;
    }

    public void setJdbcDriverPassword(final String jdbcDriverPassword) {
        this.jdbcDriverPassword = jdbcDriverPassword;
    }

}
