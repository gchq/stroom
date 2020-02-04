package stroom.authentication;

import stroom.config.common.ConnectionConfig;
import stroom.db.util.DbUtil;
import stroom.test.common.util.db.DbTestUtil;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

public class TestAuthDbConnProvider implements AuthDbConnProvider {

    @Override
    public Connection getConnection() throws SQLException {
        final ConnectionConfig connectionConfig = DbTestUtil.getOrCreateEmbeddedConnectionConfig();
        DbUtil.validate(connectionConfig);
        return DbUtil.getSingleConnection(connectionConfig);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        final ConnectionConfig connectionConfig = DbTestUtil.getOrCreateEmbeddedConnectionConfig();
        DbUtil.validate(connectionConfig);
        return DbUtil.getSingleConnection(connectionConfig);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {

    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {

    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return 0;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return null;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return null;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

}
