package stroom.db.util;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.inject.Provider;
import javax.sql.DataSource;

public abstract class DataSourceProviderProxy implements DataSource {

    private final Provider<DataSource> dataSourceProvider;

    public DataSourceProviderProxy(final Provider<DataSource> dataSourceProvider) {
        this.dataSourceProvider = dataSourceProvider;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSourceProvider.get().getConnection();
    }

    @Override
    public Connection getConnection(final String username, final String password) throws SQLException {
        return dataSourceProvider.get().getConnection(username, password);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return dataSourceProvider.get().getLogWriter();
    }

    @Override
    public void setLogWriter(final PrintWriter out) throws SQLException {
        dataSourceProvider.get().setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(final int seconds) throws SQLException {
        dataSourceProvider.get().setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return dataSourceProvider.get().getLoginTimeout();
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        return dataSourceProvider.get().unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        return dataSourceProvider.get().isWrapperFor(iface);
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return dataSourceProvider.get().getParentLogger();
    }
}
