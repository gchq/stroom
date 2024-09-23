package stroom.db.util;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;

public abstract class DataSourceProxy implements DataSource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataSourceProxy.class);

    private final DataSource dataSource;
    private final String name;

    public DataSourceProxy(final DataSource dataSource, final String name) {
        this.dataSource = dataSource;
        this.name = name;
    }

    @Override
    public Connection getConnection() throws SQLException {
        // Commenting this out for now as this is a hit on every single db call
//        if (LOGGER.isTraceEnabled()) {
//            // Useful for spotting if code is not closing connections and therefore exhausting
//            // the pool
//            if (dataSource instanceof HikariDataSource hikariDataSource) {
//                final HikariPoolMXBean hikariPoolMXBean = hikariDataSource.getHikariPoolMXBean();
//                LOGGER.trace("Datasource '{}', pool '{}' - Total: {}, active: {}, idle: {}, waiting: {}",
//                        name,
//                        hikariPoolMXBean.toString(),
//                        hikariPoolMXBean.getTotalConnections(),
//                        hikariPoolMXBean.getActiveConnections(),
//                        hikariPoolMXBean.getIdleConnections(),
//                        hikariPoolMXBean.getThreadsAwaitingConnection());
//            }
//        }
        return dataSource.getConnection();
    }

    @Override
    public Connection getConnection(final String username, final String password) throws SQLException {
        return dataSource.getConnection(username, password);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return dataSource.getLogWriter();
    }

    @Override
    public void setLogWriter(final PrintWriter out) throws SQLException {
        dataSource.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(final int seconds) throws SQLException {
        dataSource.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return dataSource.getLoginTimeout();
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        return dataSource.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        return dataSource.isWrapperFor(iface);
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return dataSource.getParentLogger();
    }

    public String getName() {
        return name;
    }
}
