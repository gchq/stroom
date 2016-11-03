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

package stroom.util.sql;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;

/**
 * Test class to explore issues with character sets between Java and MySql.
 * Currently ignored as most of the tests require human oversight. Left in, in
 * case it proves useful.
 */
@Ignore
public class TestMySQLCharacterSets {
    @BeforeClass
    public static void setup() throws SQLException {
        Connection connection = null;

        try {
            connection = getConnection();

            executeSql(connection, "DROP TABLE IF EXISTS CHAR_SET_TEST");

            executeSql(connection,
                    "CREATE TABLE IF NOT EXISTS CHAR_SET_TEST (TXT VARCHAR(255)) ENGINE=InnoDB AUTO_INCREMENT=129 DEFAULT CHARSET=latin1");

        } finally {
            try {
                connection.close();
            } catch (final Exception e) {
            }
        }
    }

    @Test
    public void testRegexOnNotSign() throws SQLException {
        Connection connection = null;
        final String tabCharValue = "a¬b";
        final String tabCharRegex = "a[¬]b(¬|$)";
        final String insertBit = "INSERT INTO CHAR_SET_TEST VALUES (";

        try {
            connection = getConnection();

            // prepared statement escape chars for us
            executeSql(connection, insertBit + "?)", new String[] { tabCharValue });

            final PreparedStatement preparedStatement = executeSql(connection,
                    "SELECT * FROM CHAR_SET_TEST WHERE TXT REGEXP ?", new String[] { tabCharRegex });

            final ResultSet resultSet = preparedStatement.getResultSet();

            int i = 0;
            String result = "";

            while (resultSet.next()) {
                result = resultSet.getString(1);
                i++;
            }

            Assert.assertEquals(1, i);
            Assert.assertEquals(tabCharValue, result);

        } finally {
            try {
                connection.close();
            } catch (final Exception e) {
            }
        }
    }

    @Test
    public void testEscapingTab() throws SQLException {
        Connection connection = null;
        final String vTab = new String(new byte[] { 11 });
        final String tabCharValue = "a" + vTab + "b" + vTab;
        final String tabCharRegex = "a[" + vTab + "]b(" + vTab + "|$)";
        final String insertBit = "INSERT INTO CHAR_SET_TEST VALUES (";

        try {
            connection = getConnection();

            // prepared statement escape chars for us
            executeSql(connection, insertBit + "?)", new String[] { tabCharValue });

            final PreparedStatement preparedStatement = executeSql(connection,
                    "SELECT * FROM CHAR_SET_TEST WHERE TXT REGEXP ?", new String[] { tabCharRegex });

            final ResultSet resultSet = preparedStatement.getResultSet();

            int i = 0;
            String result = "";

            while (resultSet.next()) {
                result = resultSet.getString(1);
                i++;
            }

            Assert.assertEquals(1, i);
            Assert.assertEquals(tabCharValue, result);

        } finally {
            try {
                connection.close();
            } catch (final Exception e) {
            }
        }
    }

    @Test
    public void testEscaping() throws SQLException {
        Connection connection = null;
        final String dirtyValue = "\\_\"_'";
        final String insertBit = "INSERT INTO CHAR_SET_TEST VALUES (";

        try {
            connection = getConnection();

            // prepared statement escape chars for us
            executeSql(connection, insertBit + "?)", new String[] { dirtyValue });

            // statement runs what it is given so need to escape dirty chars
            final String safeValue = SQLSafe.escapeChars(dirtyValue);
            executeSqlAsStatement(connection, insertBit + "'" + safeValue + "')");

        } finally {
            try {
                connection.close();
            } catch (final Exception e) {
            }
        }
    }

    @Test
    public void testDelimiter() throws SQLException {
        Connection connection = null;

        try {
            connection = getConnection();

            executeSql(connection, "INSERT INTO CHAR_SET_TEST VALUES (?)", new String[] { "a¬b¬c¬d" });
            executeSql(connection, "INSERT INTO CHAR_SET_TEST VALUES (?)", new String[] { "a\u00acb\u00acc\u00acd" });

            dumpAllRows();
        } finally {
            try {
                connection.close();
            } catch (final Exception e) {
            }
        }

    }

    @Test
    public void testString() {
        final String delim = "¬";
        final Charset charset = Charset.forName("ISO8859_1");
        final byte[] latinBytes = delim.getBytes(charset);
        final String latin1 = new String(latinBytes, charset);
        final Map<String, Charset> charsets = Charset.availableCharsets();
        System.out.println(charsets.keySet());
        System.out.println(latin1);
    }

    private static void dumpAllRows() throws SQLException {
        Connection connection = null;

        try {
            connection = getConnection();
            final PreparedStatement preparedStatement = executeSql(connection, "SELECT * FROM CHAR_SET_TEST");
            final ResultSet resultSet = preparedStatement.getResultSet();
            int i = 0;
            String result = "";

            System.out.println("---Results---");

            while (resultSet.next()) {
                result = resultSet.getString(1);
                System.out.println(result);
                i++;
            }
            System.out.println("-------------");
        } finally {
            connection.close();
        }

    }

    private static void executeSqlAsStatement(final Connection connection, final String sql) throws SQLException {
        final Statement statement = connection.createStatement();
        System.out.println("Executing sql: " + sql);
        statement.execute(sql);
    }

    private static PreparedStatement executeSql(final Connection connection, final String sql) throws SQLException {
        final PreparedStatement preparedStatement = connection.prepareStatement(sql);
        System.out.println("Executing sql: " + sql);
        preparedStatement.execute();
        return preparedStatement;
    }

    private static PreparedStatement executeSql(final Connection connection, final String sql,
            final String[] bindValues) throws SQLException {
        final PreparedStatement preparedStatement = connection.prepareStatement(sql);

        int paramIndex = 1;
        for (final String bindValue : bindValues) {
            preparedStatement.setString(paramIndex++, bindValue);
        }

        final Properties properties = preparedStatement.getConnection().getClientInfo();
        properties.list(System.out);

        System.out.println("Executing sql: " + sql);
        preparedStatement.execute();
        return preparedStatement;
    }

    private static PreparedStatement executeSqlAsCharSet(final Connection connection, final String sql,
            final String[] bindValues) throws SQLException {
        final PreparedStatement preparedStatement = connection.prepareStatement(sql);
        final Charset charset = Charset.forName("ISO8859_1");

        int paramIndex = 1;
        for (final String bindValue : bindValues) {
            final byte[] latin1 = bindValue.getBytes(charset);
            preparedStatement.setBytes(paramIndex++, latin1);
        }

        final Properties properties = preparedStatement.getConnection().getClientInfo();
        properties.list(System.out);

        System.out.println("Executing sql: " + sql);
        preparedStatement.execute();
        return preparedStatement;
    }

    public static final Connection getConnection() throws SQLException {
        final String driverClassname = "com.mysql.jdbc.Driver";
        final String driverUrl = "jdbc:mysql://localhost/stroom";
        final String driverUsername = System.getProperty("user.name");
        final String driverPassword = "password";

        if (driverClassname == null || driverUrl == null) {
            throw new RuntimeException("Properties are not set for DB connection");
        }

        try {
            Class.forName(driverClassname);
        } catch (final Exception ex) {
            throw new RuntimeException(ex);
        }

        return DriverManager.getConnection(driverUrl, driverUsername, driverPassword);
    }
}
