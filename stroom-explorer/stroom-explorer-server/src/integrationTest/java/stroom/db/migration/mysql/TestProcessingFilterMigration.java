package stroom.db.migration.mysql;

import org.junit.Ignore;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

@Ignore
public class TestProcessingFilterMigration {
    private static final String TEST_USER = "stroomuser";
    private static final String TEST_PASSWORD = "stroompassword1";

    /**
     * If you have a database on a version prior to 6.0.0.13 then you can run this test to
     * step through the various things done during this migration without having to run up stroom
     *
     * @throws Exception If anything goes wrong, just go bang
     */
    @Test
    @Ignore
    public void testMigrateOnDockerImage() throws Exception {
        final V6_0_0_9__ProcessingFilter filter = new V6_0_0_9__ProcessingFilter(false);

        final Properties connectionProps = new Properties();
        connectionProps.put("user", TEST_USER);
        connectionProps.put("password", TEST_PASSWORD);

        final Connection conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3307/stroom",
                connectionProps);

        filter.migrate(conn);

        conn.close();
    }
}
