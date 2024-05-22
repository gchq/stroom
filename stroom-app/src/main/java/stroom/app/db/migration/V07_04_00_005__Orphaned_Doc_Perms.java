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
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import org.flywaydb.core.api.migration.Context;

import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.sql.DataSource;

/**
 * Previously deletion of a doc did not delete the doc_permission records for it.
 * This migration deleted doc_permission records that are orphaned.
 */
public class V07_04_00_005__Orphaned_Doc_Perms extends AbstractCrossModuleJavaDbMigration {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(V07_04_00_005__Orphaned_Doc_Perms.class);

    private static final String BACKUP_TBL_NAME = "doc_permission_backup_V07_04_00_005";

    private final SecurityDbConnProvider securityDbConnProvider;
    private final ProcessorDbConnProvider processorDbConnProvider;
    private final ExplorerDbConnProvider explorerDbConnProvider;
    private final DocStoreDbConnProvider docStoreDbConnProvider;

    @Inject
    public V07_04_00_005__Orphaned_Doc_Perms(final SecurityDbConnProvider securityDbConnProvider,
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
        LOGGER.info("Starting purge of orphaned document permissions");
        final DurationTimer timer = DurationTimer.start();

        // Find all the doc uuids that are in use as docs, folders, proc filters and System
        final Set<String> validDocUuids = getValidDocUuids();

        // Create the empty backup table
        DbUtil.executeStatement(securityDbConnProvider, String.format("""
                create table %s as
                select *
                from doc_permission
                where 1 = 0""", BACKUP_TBL_NAME));

        AtomicInteger totalOrphanedDocCnt = new AtomicInteger(0);
        AtomicInteger totalDeleteCount = new AtomicInteger(0);

        // This tbl may be 100s of thousands of rows so do it batch wise
        DbUtil.doWithPreparedStatement(securityDbConnProvider, """
                select v.doc_uuid, v.max_id
                from (
                    select doc_uuid, max(id) max_id
                    from doc_permission
                    where doc_uuid != 0
                    group by doc_uuid
                    order by max_id) v
                where v.max_id > ?
                limit 5000""", ThrowingConsumer.unchecked(prepStmt -> {

            long lastMaxId = -1;
            while (true) {
                prepStmt.setLong(1, lastMaxId);
                int cnt = 0;
                final Set<String> orphanedDocUuids = new HashSet<>();
                try (ResultSet resultSet = prepStmt.executeQuery()) {
                    while (resultSet.next()) {
                        cnt++;
                        final String docUuid = resultSet.getString("doc_uuid");
                        // Use maxId to control where the batch starts from next time
                        final long maxId = resultSet.getLong("max_id");
                        lastMaxId = Math.max(lastMaxId, maxId);

                        if (!validDocUuids.contains(docUuid)) {
                            // doc uuid does not exist as a folder/doc/procFilter/System so it is orphaned
                            totalOrphanedDocCnt.incrementAndGet();
                            orphanedDocUuids.add(docUuid);
                        }
                    }
                }
                LOGGER.info("Fetched batch of {} aggregated doc_permission rows, " +
                                "orphaned docs: {}, cumulative orphaned docs: {}, lastMaxId: {}",
                        cnt, orphanedDocUuids.size(), totalOrphanedDocCnt, lastMaxId);

                final int deleteCount = deleteOrphanedDocs(orphanedDocUuids);
                totalDeleteCount.addAndGet(deleteCount);

                if (cnt == 0) {
                    // Found nothing so have reached the end
                    break;
                }
            }
        }));

        LOGGER.info("Completed purge of {} orphaned document permissions for {} orphaned docs in {}",
                totalDeleteCount.get(), totalOrphanedDocCnt.get(), timer);
    }

    private Set<String> getValidDocUuids() {
        final Set<String> allFolderUuids = getAllFolderUuids();
        final Set<String> processorFilterUuids = getProcessorFilterUuids();
        final Set<String> docUuids = getDocUuids();
        // Should be same as ExplorerConstants.SYSTEM_DOC_REF.getUuid() when this mig was written
        final String systemDocUuid = "0";

        final Set<String> validDocUuids = new HashSet<>(allFolderUuids.size()
                + processorFilterUuids.size()
                + docUuids.size()
                + 1);
        validDocUuids.addAll(allFolderUuids);
        validDocUuids.addAll(processorFilterUuids);
        validDocUuids.addAll(docUuids);
        validDocUuids.add(systemDocUuid);
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

            final String deleteSql = LogUtil.message("""
                    delete from doc_permission
                    where doc_uuid in ({})""", paramsStr);

            rowCnt = DbUtil.getWithPreparedStatement(
                    securityDbConnProvider, deleteSql, ThrowingFunction.unchecked(prepStmt -> {
                        int paramNo = 1;
                        for (final String docUuid : orphanedDocUuids) {
                            prepStmt.setString(paramNo++, docUuid);
                        }
                        return prepStmt.executeUpdate();
                    }));
            LOGGER.info("Deleted {} rows from doc_permission for {} orphaned doc UUIDs",
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
