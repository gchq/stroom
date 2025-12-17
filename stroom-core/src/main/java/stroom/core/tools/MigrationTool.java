/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.core.tools;

import stroom.util.ArgsUtil;
import stroom.util.io.StreamUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.StringTokenizer;

public class MigrationTool {

    static boolean ignoreError;
    static boolean update;

    public static void main(final String[] args)
            throws SQLException, NoSuchMethodException, InvocationTargetException, InstantiationException,
            IllegalAccessException, ClassNotFoundException, IOException {
        final Map<String, String> map = ArgsUtil.parse(args);

        final String url = map.get("jdbcDriverUrl");
        String clazz = map.get("jdbcDriverClassName");
        final String username = map.get("username");
        final String password = map.get("password");
        final String script = map.get("script");
        ignoreError = Boolean.TRUE.toString().equalsIgnoreCase(map.get("ignoreError"));
        update = !Boolean.FALSE.toString().equalsIgnoreCase(map.get("update"));

        if (clazz == null) {
            clazz = "com.mysql.cj.jdbc.Driver";
        }

        if (url == null || username == null || password == null || script == null) {
            throw new IllegalArgumentException(
                    "Must provide jdbcDriverUrl, jdbcDriverClassName, username, password, script");
        }

        DriverManager.registerDriver((Driver) Class.forName(clazz).getConstructor().newInstance());

        final Connection connection = DriverManager.getConnection(url, username, password);
        connection.setAutoCommit(true);

        final InputStream inputStream = MigrationTool.class.getResourceAsStream("/META-INF/" + script);
        final LineNumberReader reader = new LineNumberReader(
                new InputStreamReader(inputStream, StreamUtil.DEFAULT_CHARSET));

        final StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("#")) {
                if (update) {
                    System.out.println("SKIP  : " + line);
                }
            } else if (line.startsWith("/*")) {
                builder.append(line);
                while (!line.endsWith("*/")) {
                    builder.append(reader.readLine());
                }
                if (update) {
                    System.out.println("SKIP  : " + builder.toString());
                }
                builder.setLength(0);

            } else {
                if (line.startsWith("alter table") && line.contains("drop")) {
                    processAlterTableLine(connection, line);
                } else {
                    builder.append(line);
                    if (line.endsWith(";")) {
                        processSQL(connection, builder.toString());
                        builder.setLength(0);
                    }
                }

            }
        }

    }

    private static String stripTerm(final String arg) {
        if (arg.endsWith(";")) {
            return arg.substring(0, arg.length() - 1);
        }
        return arg;
    }

    private static void processAlterTableLine(final Connection connection, final String line) throws SQLException {
        final StringTokenizer tokenizer = new StringTokenizer(line);
        tokenizer.nextToken();
        tokenizer.nextToken();
        final String table = tokenizer.nextToken();
        tokenizer.nextToken();
        final String column = stripTerm(tokenizer.nextToken());

        final String sql = "select constraint_name from information_schema.key_column_usage  " + "where table_name = '"
                + table + "' and column_name = '" + column + "' ;";

        final String keyName = executeStringQuery(connection, sql);

        if (keyName != null) {
            executeUpdate(connection, "alter table " + table + " drop foreign key " + keyName + ";");
        }
        executeUpdate(connection, line);

    }

    private static void processSQL(final Connection connection, final String sql) throws SQLException {
        executeUpdate(connection, sql);
    }

    @SuppressWarnings("SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE")
    private static String executeStringQuery(final Connection connection, String sql) throws SQLException {
        String rtn = null;
        sql = stripTerm(sql);
        try (final Statement statement = connection.createStatement()) {
            try (final ResultSet resultSet = statement.executeQuery(sql)) {
                if (resultSet.next()) {
                    rtn = resultSet.getString(1);
                }
            }
        }
        return rtn;
    }

    @SuppressWarnings("SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE")
    private static void executeUpdate(final Connection connection, String sql) throws SQLException {
        try {
            sql = stripTerm(sql);

            if (update) {
                System.out.println("RUNNING : " + sql);
                try (final Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                }
            } else {
                System.out.println(sql + ";");
            }
        } catch (final SQLException sqlException) {
            if (!ignoreError) {
                throw sqlException;
            } else {
                System.out.println("IGNORE : " + sqlException.getMessage());
            }
        }
    }

}
