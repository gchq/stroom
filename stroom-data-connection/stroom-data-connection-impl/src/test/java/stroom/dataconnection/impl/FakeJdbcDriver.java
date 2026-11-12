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

import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/// A JDBC driver for tests that answers `jdbc:stroomtest:` URLs with Mockito connections.
///
/// Embedded databases are refused by Data Connections, so tests cannot use one. This driver records
/// every connection and the properties it was opened with, so a test can check what Stroom asked for.
final class FakeJdbcDriver implements Driver {

    static final String URL_PREFIX = "jdbc:stroomtest:";
    static final String URL = URL_PREFIX + "hr";
    static final String ALLOWED_URL_PATTERN = "^jdbc:stroomtest:.*$";

    private final List<Connection> connections = new CopyOnWriteArrayList<>();
    private final List<Properties> connectionProperties = new CopyOnWriteArrayList<>();

    /// Creates the driver and registers it with [DriverManager].
    ///
    /// @return The registered driver, to be passed to [#deregister()] when the test ends.
    static FakeJdbcDriver register() throws SQLException {
        final FakeJdbcDriver driver = new FakeJdbcDriver();
        DriverManager.registerDriver(driver);
        return driver;
    }

    /// Removes the driver from [DriverManager].
    void deregister() throws SQLException {
        DriverManager.deregisterDriver(this);
    }

    /// @return Every connection opened so far.
    List<Connection> getConnections() {
        return connections;
    }

    /// @return The properties each connection was opened with, in order.
    List<Properties> getConnectionProperties() {
        return connectionProperties;
    }

    @Override
    public Connection connect(final String url, final Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }
        final DatabaseMetaData metaData = Mockito.mock(DatabaseMetaData.class);
        Mockito.when(metaData.getDatabaseProductName()).thenReturn("FakeDB");
        Mockito.when(metaData.getDatabaseProductVersion()).thenReturn("1.2.3");
        Mockito.when(metaData.getDriverName()).thenReturn("Fake Driver");
        Mockito.when(metaData.getDriverVersion()).thenReturn("4.5");
        Mockito.when(metaData.getUserName()).thenReturn(info.getProperty("user"));

        final Connection connection = Mockito.mock(Connection.class);
        Mockito.when(connection.isValid(Mockito.anyInt())).thenReturn(true);
        Mockito.when(connection.getAutoCommit()).thenReturn(true);
        Mockito.when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
        Mockito.when(connection.getMetaData()).thenReturn(metaData);

        connections.add(connection);
        connectionProperties.add(info);
        return connection;
    }

    @Override
    public boolean acceptsURL(final String url) {
        return url != null && url.startsWith(URL_PREFIX);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(final String url, final Properties info) {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        return 1;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getLogger(FakeJdbcDriver.class.getName());
    }
}
