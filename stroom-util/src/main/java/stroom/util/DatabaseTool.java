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

package stroom.util;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseTool extends AbstractCommandLineTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DatabaseTool.class);

    private String jdbcDriverClassName;
    private String jdbcDriverUrl;
    private String jdbcDriverUsername;
    private String jdbcDriverPassword;

    public static void main(final String[] args) {
        new DatabaseTool().doMain(args);
    }

    @Override
    public void run() {
        try {
            try (final Connection connection = getConnection()) {
                try (final Statement statement = connection.createStatement()) {
                    statement.execute("select 1=1");
                }
            }
            System.out.println("Connected!!");

        } catch (final SQLException e) {
            e.printStackTrace();
        }
    }

    protected Connection getConnection() {
        final Connection connection;
        try {
            Class.forName(jdbcDriverClassName);
        } catch (final ClassNotFoundException ex) {
            throw new RuntimeException(ex);
        }
        try {
            connection = DriverManager.getConnection(jdbcDriverUrl, jdbcDriverUsername, jdbcDriverPassword);
            LOGGER.info(() -> "getConnection() - Connected !! (" + jdbcDriverClassName + "," + jdbcDriverUrl + ")");
            return connection;
        } catch (final SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
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
