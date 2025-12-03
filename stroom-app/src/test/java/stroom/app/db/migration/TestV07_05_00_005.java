/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.app.db.migration;

import stroom.db.util.DbUtil;
import stroom.docstore.impl.db.DocStoreDbConnProvider;
import stroom.explorer.impl.db.ExplorerDbConnProvider;
import stroom.processor.impl.db.ProcessorDbConnProvider;
import stroom.security.impl.db.SecurityDbConnProvider;
import stroom.util.exception.ThrowingConsumer;
import stroom.util.exception.ThrowingFunction;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled // Migration no longer does anything so disabling the test
public class TestV07_05_00_005 extends AbstractCrossModuleMigrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestV07_05_00_005.class);

    static {
        // Use a small batch size to test batching properly
        V07_05_00_005__Orphaned_Doc_Perms.BATCH_SIZE = 2;
    }

    @Override
    public Class<? extends AbstractCrossModuleMigrationTestData> getTestDataClass() {
        return TestData.class;
    }

    @Override
    Class<? extends AbstractCrossModuleJavaDbMigration> getTargetClass() {
        return V07_05_00_005__Orphaned_Doc_Perms.class;
    }

    @Test
    void test() {
        // By this point the database will have been migrated up to the target version
        // with the test data having been applied prior to the target migration running.
        // If it fails before you get here then there is an exception in the migration
        // or the test data.

        // If you get here then you probably want to assert something about the migrated
        // state.

        // Assert table counts BEFORE the migration under test is run
        assertTableCountBeforeMigration("processor", 2);
        assertTableCountBeforeMigration("processor_filter", 2);
        assertTableCountBeforeMigration("doc", 2);
        assertTableCountBeforeMigration("explorer_node", 4);
        assertTableCountBeforeMigration("stroom_user", 2);
        // 5 docs per usr, 2 users, 3 perms per doc
        final int expDocPermCntBefore = 5 * 2 * 3;
        assertTableCountBeforeMigration("doc_permission", expDocPermCntBefore);

        final ProcessorDbConnProvider processorDbConnProvider = getDatasource(ProcessorDbConnProvider.class);
        final SecurityDbConnProvider securityDbConnProvider = getDatasource(SecurityDbConnProvider.class);
        final ExplorerDbConnProvider explorerDbConnProvider = getDatasource(ExplorerDbConnProvider.class);
        final DocStoreDbConnProvider docStoreDbConnProvider = getDatasource(DocStoreDbConnProvider.class);

        // Assert table counts AFTER the migration under test is run
        assertTableCountAfterMigration(processorDbConnProvider, "processor", 2);
        assertTableCountAfterMigration(processorDbConnProvider, "processor_filter", 2);
        assertTableCountAfterMigration(docStoreDbConnProvider, "doc", 2);
        assertTableCountAfterMigration(explorerDbConnProvider, "explorer_node", 4);
        assertTableCountAfterMigration(securityDbConnProvider, "stroom_user", 2);
        // 2 docs each with 2 users, get purged with 3 perms each
        final int purgedDocCnt = 2 * 2 * 3;
        final int expDocPermCntAfter = expDocPermCntBefore - purgedDocCnt;
        assertTableCountAfterMigration(securityDbConnProvider, "doc_permission", expDocPermCntAfter);

        // Make sure the right amount of recs have gone in the backup table
        assertTableCountAfterMigration(
                securityDbConnProvider, V07_05_00_005__Orphaned_Doc_Perms.BACKUP_TBL_NAME, purgedDocCnt);

        // Make sure the orphaned docs are not their anymore
        assertThat(getCount(securityDbConnProvider, """
                select count(*)
                from doc_permission
                where doc_uuid like 'unknown_uuid_%'"""))
                .isEqualTo(0);

        // Make sure the orphaned docs are in the backup table
        assertThat(getCount(securityDbConnProvider, LogUtil.message("""
                select count(*)
                from {}
                where doc_uuid like 'unknown_uuid_%'""", V07_05_00_005__Orphaned_Doc_Perms.BACKUP_TBL_NAME)))
                .isEqualTo(12);
    }

    private static void assertTableCountBeforeMigration(final String tableName, final int expected) {
        assertThat(TestData.TEST_DATA_TABLE_COUNTS_MAP.get(tableName))
                .isEqualTo(expected);
    }

    private static void assertTableCountAfterMigration(
            final DataSource dataSource,
            final String tableName,
            final int expected) {
        final int cnt = DbUtil.countEntity(dataSource, tableName);
        LOGGER.info("Table count for {}: {}", tableName, cnt);
        assertThat(cnt).isEqualTo(expected);
    }

    private int getCount(final DataSource dataSource, final String sql) {
        return DbUtil.getWithPreparedStatement(dataSource, sql, ThrowingFunction.unchecked(preparedStatement -> {
            final ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt(1);
            } else {
                throw new RuntimeException("No data found");
            }
        }));
    }


    // --------------------------------------------------------------------------------


    static class TestData extends AbstractCrossModuleMigrationTestData {

        private static final String SYSTEM_DOC_UUID = V07_05_00_005__Orphaned_Doc_Perms.SYSTEM_DOC_UUID;

        public static final String PROC_SQL = """
                INSERT INTO processor (
                version,
                create_time_ms,
                create_user,
                update_time_ms,
                update_user,
                uuid,
                pipeline_uuid,
                enabled,
                deleted)
                VALUES (
                1,
                0,
                'jbloggs',
                0,
                'jbloggs',
                'proc_uuid_0001',
                'pipe_uuid_0001',
                1,
                0)""";

        public static final String TRACKER_SQL = """
                INSERT INTO processor_filter_tracker (
                `version`,
                `min_meta_id`,
                `min_event_id`,
                `status`)
                VALUES (
                1,
                0,
                0,
                0)""";

        public static final String PROC_FILT_SQL = """
                INSERT INTO processor_filter (
                version,
                create_time_ms,
                create_user,
                update_time_ms,
                update_user,
                uuid,
                fk_processor_id,
                fk_processor_filter_tracker_id,
                data,
                priority,
                reprocess,
                enabled,
                deleted,
                max_processing_tasks)
                VALUES (
                1,
                0,
                'jbloggs',
                0,
                'jbloggs',
                'proc_filt_uuid_0001',
                <fk_processor_id>,
                <fk_processor_filter_tracker_id>,
                'my data',
                10,
                0,
                1,
                0,
                0)""";

        public static final String NODE_SQL = """
                INSERT INTO explorer_node (
                type,
                uuid,
                name)
                VALUES (
                ?,
                ?,
                ?)""";

        public static final String DOC_SQL = """
                INSERT INTO doc (
                type,
                uuid,
                name,
                ext)
                VALUES (
                ?,
                ?,
                ?,
                'meta')""";

        public static final String USER_SQL = """
                INSERT INTO stroom_user (
                version,
                create_time_ms,
                create_user,
                update_time_ms,
                update_user,
                name,
                uuid,
                display_name,
                is_group,
                enabled)
                VALUES (
                1,
                0,
                'jbloggs',
                0,
                'jbloggs',
                ?,
                ?,
                ?,
                0,
                1)""";

        public static final String DOC_PERM_SQL = """
                INSERT INTO doc_permission (
                user_uuid,
                doc_uuid,
                permission)
                VALUES (
                ?,
                ?,
                ?)""";

        // static so we can pass info between the TestData class and the test
        private static final Map<String, Integer> TEST_DATA_TABLE_COUNTS_MAP = new HashMap<>();

        private final SecurityDbConnProvider securityDbConnProvider;
        private final ProcessorDbConnProvider processorDbConnProvider;
        private final ExplorerDbConnProvider explorerDbConnProvider;
        private final DocStoreDbConnProvider docStoreDbConnProvider;

        @Inject
        TestData(final TestState testState,
                 final SecurityDbConnProvider securityDbConnProvider,
                 final ProcessorDbConnProvider processorDbConnProvider,
                 final ExplorerDbConnProvider explorerDbConnProvider,
                 final DocStoreDbConnProvider docStoreDbConnProvider) {
            super(testState);

            this.securityDbConnProvider = securityDbConnProvider;
            this.processorDbConnProvider = processorDbConnProvider;
            this.explorerDbConnProvider = explorerDbConnProvider;
            this.docStoreDbConnProvider = docStoreDbConnProvider;
        }

        @Override
        void setupTestData() throws Exception {
            // This is just a test, so not bothering with prep stmt params

            LOGGER.info("Create processor records");
            final long proc1Id = insertReturningId(processorDbConnProvider, PROC_SQL);
            final long proc2Id = insertReturningId(processorDbConnProvider,
                    PROC_SQL.replace("_0001", "_0002"));

            LOGGER.info("Create tracker records");
            final long tracker1Id = insertReturningId(processorDbConnProvider, TRACKER_SQL);
            final long tracker2Id = insertReturningId(processorDbConnProvider, TRACKER_SQL);

            LOGGER.info("Create filter records");
            final long filter1Id = insertReturningId(
                    processorDbConnProvider,
                    PROC_FILT_SQL.replace("<fk_processor_id>", String.valueOf(proc1Id))
                            .replace("<fk_processor_filter_tracker_id>", String.valueOf(tracker1Id)));
            final long filter2Id = insertReturningId(
                    processorDbConnProvider,
                    PROC_FILT_SQL.replace("<fk_processor_id>", String.valueOf(proc2Id))
                            .replace("<fk_processor_filter_tracker_id>", String.valueOf(tracker2Id))
                            .replace("_0001", "_0002"));

            LOGGER.info("Create doc and explorer_node records");
            final String doc1Uuid = insertDoc("doc1", "Dictionary", "doc_uuid_0001");
            final String doc2Uuid = insertDoc("doc2", "Dictionary", "doc_uuid_0002");
            final String folder1Uuid = insertNode("folder1", "Folder", "doc_uuid_0003");
            final String folder2Uuid = insertNode("folder2", "Folder", "doc_uuid_0004");

            insertUser("user_uuid_0001");
            insertUser("user_uuid_0002");

            LOGGER.info("Create doc and explorer_node records");
            createDocPerms("user_uuid_0001", SYSTEM_DOC_UUID);
            createDocPerms("user_uuid_0001", "doc_uuid_0001");
            createDocPerms("user_uuid_0001", "doc_uuid_0002");
            // These are orphans
            createDocPerms("user_uuid_0001", "unknown_uuid_0001");
            createDocPerms("user_uuid_0001", "unknown_uuid_0002");

            createDocPerms("user_uuid_0002", SYSTEM_DOC_UUID);
            createDocPerms("user_uuid_0002", "doc_uuid_0001");
            createDocPerms("user_uuid_0002", "doc_uuid_0002");
            // These are orphans
            createDocPerms("user_uuid_0002", "unknown_uuid_0001");
            createDocPerms("user_uuid_0002", "unknown_uuid_0002");

            // Capture the table counts prior to migration so that we can assert they are what we
            // expect when the test runs.
            recordTableCounts();
        }

        private void recordTableCounts() {
            recordTableCounts(processorDbConnProvider, "processor");
            recordTableCounts(processorDbConnProvider, "processor_filter");
            recordTableCounts(docStoreDbConnProvider, "doc");
            recordTableCounts(explorerDbConnProvider, "explorer_node");
            recordTableCounts(securityDbConnProvider, "stroom_user");
            recordTableCounts(securityDbConnProvider, "doc_permission");
        }

        private void recordTableCounts(final DataSource dataSource,
                                       final String tableName) {
            final int cnt = DbUtil.countEntity(dataSource, tableName);
            LOGGER.info("Table count for {}: {}", tableName, cnt);
            TEST_DATA_TABLE_COUNTS_MAP.put(tableName, cnt);
        }

        private void createDocPerms(final String userUuid, final String docUuid) {
            Stream.of("Use", "Read", "Update")
                    .forEach(perm -> {
                        DbUtil.doWithPreparedStatement(
                                explorerDbConnProvider,
                                DOC_PERM_SQL,
                                ThrowingConsumer.unchecked(prepStmt -> {
                                    prepStmt.setString(1, userUuid);
                                    prepStmt.setString(2, docUuid);
                                    prepStmt.setString(3, perm);
                                    final int cnt = prepStmt.executeUpdate();
                                    LOGGER.debug("Inserted {} doc_permission records", cnt);
                                }));
                    });
        }

        private long insertReturningId(final DataSource dataSource, final String insertSql) {
            return DbUtil.getWithPreparedStatement(dataSource,
                    insertSql,
                    true,
                    ThrowingFunction.unchecked(prepStmt -> {
                        final int updateCnt = prepStmt.executeUpdate();
                        if (updateCnt != 1) {
                            throw new RuntimeException("Expecting 1 back");
                        }
                        final ResultSet generatedKeys = prepStmt.getGeneratedKeys();
                        if (!generatedKeys.next()) {
                            throw new RuntimeException("Expecting 1 back");
                        }
                        return generatedKeys.getLong(1);
                    }));
        }

        private String insertNode(final String name, final String type, final String uuid) {
            DbUtil.doWithPreparedStatement(
                    explorerDbConnProvider,
                    NODE_SQL,
                    ThrowingConsumer.unchecked(prepStmt -> {
                        prepStmt.setString(1, type);
                        prepStmt.setString(2, uuid);
                        prepStmt.setString(3, name);
                        final int cnt = prepStmt.executeUpdate();
                        LOGGER.debug("Inserted {} node records", cnt);
                    }));
            return uuid;
        }

        private String insertDoc(final String name, final String type, final String uuid) {
            // Also insert a node for the doc
            insertNode(name, type, uuid);

            DbUtil.doWithPreparedStatement(
                    docStoreDbConnProvider,
                    DOC_SQL,
                    ThrowingConsumer.unchecked(prepStmt -> {
                        prepStmt.setString(1, type);
                        prepStmt.setString(2, uuid);
                        prepStmt.setString(3, name);
                        final int cnt = prepStmt.executeUpdate();
                        LOGGER.debug("Inserted {} doc records", cnt);
                    }));
            return uuid;
        }

        private String insertUser(final String uuid) {
            DbUtil.doWithPreparedStatement(
                    docStoreDbConnProvider,
                    USER_SQL,
                    ThrowingConsumer.unchecked(prepStmt -> {
                        prepStmt.setString(1, "user__" + uuid);
                        prepStmt.setString(2, uuid);
                        prepStmt.setString(3, "user__" + uuid);
                        final int cnt = prepStmt.executeUpdate();
                        LOGGER.debug("Inserted {} user records", cnt);
                    }));
            return uuid;
        }
    }
}
