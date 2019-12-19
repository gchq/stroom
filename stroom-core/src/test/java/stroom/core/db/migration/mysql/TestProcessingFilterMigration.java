package stroom.core.db.migration.mysql;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import stroom.config.common.ConnectionConfig;
import stroom.db.util.DbUtil;
import stroom.test.common.util.db.DbTestUtil;

import java.sql.Connection;

@Disabled
class TestProcessingFilterMigration {
    /**
     * If you have a database on a version prior to 6.0.0.13 then you can run this test to
     * step through the various things done during this migration without having to run up stroom
     *
     * @throws Exception If anything goes wrong, just go bang
     */
    @Test
    @Disabled
    void testMigrateOnDockerImage() throws Exception {
        final V6_0_0_9__ProcessingFilter filter = new V6_0_0_9__ProcessingFilter(false);

        final ConnectionConfig connectionConfig = DbTestUtil.getOrCreateEmbeddedConnectionConfig();
        DbUtil.validate(connectionConfig);
        try (final Connection conn = DbUtil.getSingleConnection(connectionConfig)) {
            filter.migrate(conn);
        }
    }
}
