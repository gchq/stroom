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
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import org.flywaydb.core.api.migration.Context;

import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.sql.DataSource;

/**
 * Previously deletion of a doc or proc_filter did not delete the doc_permission records for it.
 * This migration deletes any doc_permission records for a doc_uuid that cannot be found
 * in doc, processor_filter or explorer_node and is not the System doc (uuid == 0).
 * <p>
 * The migration copies the orphaned doc_permission records to a backup table
 * ({@link V07_05_00_005__Orphaned_Doc_Perms#BACKUP_TBL_NAME}) before deleting them.
 * </p>
 */
@Deprecated // See comments in migrate method
public class V07_05_00_005__Orphaned_Doc_Perms extends AbstractCrossModuleJavaDbMigration {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(V07_05_00_005__Orphaned_Doc_Perms.class);

    static final String SYSTEM_DOC_UUID = "0";
    // Mutable so we can test with a diff value
    static int BATCH_SIZE = 1_000;

    static final String BACKUP_TBL_NAME = "doc_permission_backup_V07_05_00_005";

    private final SecurityDbConnProvider securityDbConnProvider;
    private final ProcessorDbConnProvider processorDbConnProvider;
    private final ExplorerDbConnProvider explorerDbConnProvider;
    private final DocStoreDbConnProvider docStoreDbConnProvider;

    @Inject
    public V07_05_00_005__Orphaned_Doc_Perms(final SecurityDbConnProvider securityDbConnProvider,
                                             final ProcessorDbConnProvider processorDbConnProvider,
                                             final ExplorerDbConnProvider explorerDbConnProvider,
                                             final DocStoreDbConnProvider docStoreDbConnProvider) {
        this.securityDbConnProvider = securityDbConnProvider;
        this.processorDbConnProvider = processorDbConnProvider;
        this.explorerDbConnProvider = explorerDbConnProvider;
        this.docStoreDbConnProvider = docStoreDbConnProvider;
    }

    @Override
    public void migrate(final Context context) throws Exception {


        // *************************************************************************
        // Changing this migration to do nothing as it is not safe to run long term
        // as we can't be sure what state the four modules are in and whether
        // the tables exist or have changed since this was written.
        // Also, it is a bit moot as all the perms tables were migrated in v7.6
        // *************************************************************************


//        LOGGER.info("Starting purge of orphaned document permissions");
//        final DurationTimer timer = DurationTimer.start();
//
//        // Create the empty backup table
//        final boolean doesBackupTblExist = DbUtil.doesTableExist(
//                securityDbConnProvider.getConnection(), BACKUP_TBL_NAME);
//        LOGGER.info("doesBackupTblExist: {}", doesBackupTblExist);
//
//        if (!doesBackupTblExist) {
//            // Find all the doc uuids that are in use as docs, folders, proc filters and System
//            final Set<String> validDocUuids = getValidDocUuids();
//
//            LOGGER.info("Creating empty backup table {}", BACKUP_TBL_NAME);
//            DbUtil.executeStatement(securityDbConnProvider, String.format("""
//                    create table %s (primary key (id)) as
//                    select *
//                    from doc_permission
//                    where 1 = 0""", BACKUP_TBL_NAME));
//            doMigration(validDocUuids, timer);
//        } else {
//            LOGGER.info("Table {} already exists, nothing to do", BACKUP_TBL_NAME);
//        }
    }

    private void doMigration(final Set<String> validDocUuids, final DurationTimer timer) {
        final AtomicInteger totalOrphanedDocCnt = new AtomicInteger(0);
        final AtomicInteger totalDeleteCount = new AtomicInteger(0);

        // This tbl may be 100s of thousands of rows (hopefully a lot less after aggregation) so do it batch wise.
        // We can't just do all this in one bit of sql as we can't be sure each module is in the same DB as they are
        // logically separate.
        DbUtil.doWithPreparedStatement(
                securityDbConnProvider,
                """
                        select v.doc_uuid, v.max_id, v.perm_cnt
                        from (
                            select doc_uuid, max(id) max_id, count(*) perm_cnt
                            from doc_permission
                            where doc_uuid != '0'
                            group by doc_uuid
                            order by max_id) v
                        where v.max_id > ?
                        limit ?;
                        """,
                ThrowingConsumer.unchecked(prepStmt -> {
                    long lastMaxId = -1;
                    while (true) {
                        prepStmt.setLong(1, lastMaxId);
                        prepStmt.setLong(2, BATCH_SIZE);
                        int rowsFoundInBatch = 0;
                        final Set<String> orphanedDocUuids = new HashSet<>();
                        try (final ResultSet resultSet = prepStmt.executeQuery()) {
                            while (resultSet.next()) {
                                rowsFoundInBatch++;
                                final String docUuid = resultSet.getString("doc_uuid");
                                // Use maxId to control where the batch starts from next time
                                final long maxId = resultSet.getLong("max_id");
                                final long permCnt = resultSet.getLong("perm_cnt");
                                lastMaxId = Math.max(lastMaxId, maxId);

                                if (!validDocUuids.contains(docUuid)) {
                                    // doc uuid does not exist as a folder/doc/procFilter/System, so it is orphaned
                                    totalOrphanedDocCnt.incrementAndGet();
                                    orphanedDocUuids.add(docUuid);
                                    LOGGER.info("Found {} orphaned doc_permission records for doc '{}' with " +
                                                "max doc_permission ID {}",
                                            permCnt, docUuid, maxId);
                                }
                            }
                        }
                        if (rowsFoundInBatch == 0) {
                            // Found nothing so have reached the end
                            break;
                        }
                        LOGGER.info("Batch summary - total docs: {}, orphaned docs: {}, " +
                                    "cumulative orphaned docs: {}, max doc_permission ID: {}",
                                rowsFoundInBatch, orphanedDocUuids.size(), totalOrphanedDocCnt, lastMaxId);

                        final int deleteCount = deleteOrphanedDocs(orphanedDocUuids);
                        totalDeleteCount.addAndGet(deleteCount);
                    }
                }));

        LOGGER.info("Completed purge of {} orphaned document permissions for {} orphaned docs in {}. " +
                    "All purged data has been copied to table '{}'",
                totalDeleteCount.get(), totalOrphanedDocCnt.get(), timer, BACKUP_TBL_NAME);
    }

    private Set<String> getValidDocUuids() {
        final Set<String> allFolderUuids = getAllFolderUuids();
        final Set<String> processorFilterUuids = getProcessorFilterUuids();
        final Set<String> docUuids = getDocUuids();
        // Should be same as ExplorerConstants.SYSTEM_DOC_REF.getUuid() when this mig was written

        final Set<String> validDocUuids = new HashSet<>(allFolderUuids.size()
                                                        + processorFilterUuids.size()
                                                        + docUuids.size()
                                                        + 1);
        validDocUuids.addAll(allFolderUuids);
        validDocUuids.addAll(processorFilterUuids);
        validDocUuids.addAll(docUuids);
        validDocUuids.add(SYSTEM_DOC_UUID);
        return validDocUuids;
    }

    private int deleteOrphanedDocs(final Set<String> orphanedDocUuids) {
        if (!orphanedDocUuids.isEmpty()) {
            final String paramsStr = orphanedDocUuids.stream()
                    .map(uuid -> "?")
                    .collect(Collectors.joining(","));

            final String insertSql = String.format("""
                    insert into %s
                    select * from doc_permission
                    where doc_uuid in (%s)""", BACKUP_TBL_NAME, paramsStr);

            int rowCnt = DbUtil.getWithPreparedStatement(
                    securityDbConnProvider, insertSql, ThrowingFunction.unchecked(prepStmt -> {
                        int paramNo = 1;
                        for (final String docUuid : orphanedDocUuids) {
                            prepStmt.setString(paramNo++, docUuid);
                        }
                        return prepStmt.executeUpdate();
                    }));
            LOGGER.info("Inserted {} rows into {} for {} orphaned doc UUIDs",
                    rowCnt, BACKUP_TBL_NAME, orphanedDocUuids.size());

            final String deleteSql = String.format("""
                    delete from doc_permission
                    where doc_uuid in (%s)""", paramsStr);

            rowCnt = DbUtil.getWithPreparedStatement(
                    securityDbConnProvider, deleteSql, ThrowingFunction.unchecked(prepStmt -> {
                        int paramNo = 1;
                        for (final String docUuid : orphanedDocUuids) {
                            prepStmt.setString(paramNo++, docUuid);
                        }
                        return prepStmt.executeUpdate();
                    }));
            LOGGER.info("Deleted {} rows from doc_permission for {} orphaned doc UUIDs.",
                    rowCnt, orphanedDocUuids.size());
            return rowCnt;
        } else {
            return 0;
        }
    }

    private Set<String> getAllFolderUuids() {
        final String sql = """
                select distinct uuid
                from explorer_node
                where type = 'Folder'""";
        return getUuids(explorerDbConnProvider, sql, "uuid", "folder");
    }

    private Set<String> getProcessorFilterUuids() {
        final String sql = """
                select distinct uuid
                from processor_filter""";
        return getUuids(processorDbConnProvider, sql, "uuid", "processor filter");
    }

    private Set<String> getDocUuids() {
        final String sql = """
                select distinct uuid
                from doc""";
        return getUuids(docStoreDbConnProvider, sql, "uuid", "doc");
    }

    private Set<String> getUuids(final DataSource dataSource,
                                 final String sql,
                                 final String colName,
                                 final String name) {
        return DbUtil.getWithPreparedStatement(dataSource, sql, ThrowingFunction.unchecked(prepStmt -> {
            final ResultSet resultSet = prepStmt.executeQuery();
            final Set<String> uuids = new HashSet<>();
            while (resultSet.next()) {
                uuids.add(resultSet.getString(colName));
            }
            LOGGER.info("Found {} {} UUIDs", uuids.size(), name);
            return uuids;
        }));
    }
}
