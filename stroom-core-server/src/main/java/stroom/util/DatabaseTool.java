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
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.server.util.ConnectionUtil;

public class DatabaseTool extends AbstractCommandLineTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseTool.class);

    private String jdbcDriverClassName;
    private String jdbcDriverUrl;
    private String jdbcDriverUsername;
    private String jdbcDriverPassword;
    private Connection connection;

    @Override
    public void run() {
        try {
            try (Connection connection = getConnection()) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("select 1=1");
                    statement.close();
                }
                connection.close();
            }
            System.out.println("Connected!!");

        } catch (final Exception ex) {
            ex.printStackTrace();
        }
    }

    protected Connection getConnection() throws SQLException {
        if (connection == null) {
            try {
                Class.forName(jdbcDriverClassName);
            } catch (final ClassNotFoundException ex) {
                throw new RuntimeException(ex);
            }
            connection = DriverManager.getConnection(jdbcDriverUrl, jdbcDriverUsername, jdbcDriverPassword);
            LOGGER.info("getConnection() - Connected !! (%s,%s)", jdbcDriverClassName, jdbcDriverUrl);
        }
        return connection;
    }

    protected void closeConnection() {
        if (connection != null) {
            ConnectionUtil.close(connection);
            connection = null;
        }
    }

    public static void main(final String[] args) throws Exception {
        new DatabaseTool().doMain(args);
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
