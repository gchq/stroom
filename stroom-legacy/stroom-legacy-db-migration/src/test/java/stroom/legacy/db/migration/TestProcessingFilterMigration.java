package stroom.legacy.db.migration;

import stroom.test.common.util.db.DbTestUtil;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

@Disabled
@Deprecated
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

        try (final Connection conn = DbTestUtil.createTestDataSource().getConnection()) {
            filter.migrate(conn);
        }
    }
}
