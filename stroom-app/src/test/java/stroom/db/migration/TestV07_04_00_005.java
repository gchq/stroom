package stroom.db.migration;

import stroom.db.util.DbUtil;
import stroom.docstore.impl.db.DocStoreDbConnProvider;
import stroom.explorer.impl.db.ExplorerDbConnProvider;
import stroom.processor.impl.db.ProcessorDbConnProvider;
import stroom.security.impl.db.SecurityDbConnProvider;

import jakarta.inject.Inject;

public class TestV07_04_00_005 extends AbstractCrossModuleDbMigrationTest {

    private final SecurityDbConnProvider securityDbConnProvider;
    private final ProcessorDbConnProvider processorDbConnProvider;
    private final ExplorerDbConnProvider explorerDbConnProvider;
    private final DocStoreDbConnProvider docStoreDbConnProvider;

    @Inject
    public TestV07_04_00_005(final SecurityDbConnProvider securityDbConnProvider,
                             final ProcessorDbConnProvider processorDbConnProvider,
                             final ExplorerDbConnProvider explorerDbConnProvider,
                             final DocStoreDbConnProvider docStoreDbConnProvider) {
        this.securityDbConnProvider = securityDbConnProvider;
        this.processorDbConnProvider = processorDbConnProvider;
        this.explorerDbConnProvider = explorerDbConnProvider;
        this.docStoreDbConnProvider = docStoreDbConnProvider;
    }

    @Override
    void setupTestData() throws Exception {

    }

    private int createProcessor() {
        final String sql = """
                INSERT INTO `processor`
                (`id`,
                `version`,
                `create_time_ms`,
                `create_user`,
                `update_time_ms`,
                `update_user`,
                `uuid`,
                `task_type`,
                `pipeline_uuid`,
                `enabled`,
                `deleted`)
                VALUES
                (<{id: }>,
                <{version: }>,
                <{create_time_ms: }>,
                <{create_user: }>,
                <{update_time_ms: }>,
                <{update_user: }>,
                <{uuid: }>,
                <{task_type: }>,
                <{pipeline_uuid: }>,
                <{enabled: 0}>,
                <{deleted: 0}>);
                                """;

        DbUtil.getWithPreparedStatement(processorDbConnProvider, sql, true, prepStmt -> {
            prepStmt.

        })
    }
}
