package stroom.auth;

import org.testcontainers.containers.MySQLContainer;
import stroom.auth.resources.support.Database_IT;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

public class TestAuthDbConnProvider implements AuthDbConnProvider{
    private MySQLContainer mysql;

    public TestAuthDbConnProvider(MySQLContainer mysql){
        this.mysql = mysql;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(mysql.getJdbcUrl(), Database_IT.JDBC_USER, Database_IT.JDBC_PASSWORD);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return DriverManager.getConnection(mysql.getJdbcUrl(), username, password);
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
