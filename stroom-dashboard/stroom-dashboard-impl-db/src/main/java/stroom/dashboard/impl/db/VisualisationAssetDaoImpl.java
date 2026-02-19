package stroom.dashboard.impl.db;

import stroom.dashboard.impl.visualisation.VisualisationAssetDao;
import stroom.db.util.JooqUtil;
import stroom.importexport.api.ByteArrayImportExportAsset;
import stroom.importexport.api.ImportExportAsset;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.visualisation.shared.VisualisationAsset;
import stroom.visualisation.shared.VisualisationAssets;
import stroom.dashboard.impl.db.jooq.Tables;

import com.google.common.hash.Hashing;
import com.google.inject.Inject;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.Record3;
import org.jooq.Record5;
import org.jooq.Result;
import org.jooq.Table;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * DB implementation of the DAO.
 * The DB has three tables:
 * <ol>
 *     <li>The main 'live' table, where assets are permanently stored for retrieval via the servlet</li>
 *     <li>The draft table, where assets are stored when they are being edited by the user
 *     and before they are 'saved'</li>
 *     <li>The update_delete table. If there is a bug in the UI it might be possible for
 *     assets to be 'saved' twice. The first will copy the assets from the draft table to the live
 *     table, then delete everything in the draft table. The second call will delete the assets in the live
 *     table and copy the now empty draft table into the live table. The effect is that users will lose
 *     their assets permanently.</li>
 *     <p>The only situation that should result in an empty draft table that should be copied into live
 *     is when the last operation was a DELETE. Thus the update_delete table gets an entry when the last
 *     operation was a delete and therefore an empty draft table can be saved to live. </p>
 *     <p>While this situation should not occur, it would upset the user if it did, thus relying
 *     on perfect GWT and JavaScript seems inadvisable.</p>
 * </ol>
 *
 */
public class VisualisationAssetDaoImpl implements VisualisationAssetDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(VisualisationAssetDaoImpl.class);

    /** Byte value for true */
    private static final byte BYTE_TRUE = 1;

    /** Byte value for false */
    private static final byte BYTE_FALSE = 0;

    /** Empty file content */
    private static final byte[] EMPTY_CONTENT = new byte[0];

    /** First character in a SQL SUBSTRING function */
    private static final int START_OF_SUBSTRING = 1;

    /** Maximum length of content that users can edit = 128KiB */
    private static final int MAX_EDITABLE_CONTENT_LENGTH = 1024 * 128;

    /** Bootstraps connection */
    private final VisualisationAssetDbConnProvider connProvider;

    @SuppressWarnings("unused")
    @Inject
    VisualisationAssetDaoImpl(final VisualisationAssetDbConnProvider connProvider) {
        this.connProvider = connProvider;
    }

    /**
     * Utility method to convert a Result set into a list of assets.
     * @param result The result from Jooq
     * @return A list of assets;
     */
    private List<VisualisationAsset>
    resultToAssets(final Result<Record3<String, String, Byte>> result) {
        final List<VisualisationAsset> assets = new ArrayList<>(result.size());
        for (final Record3<String, String, Byte> record : result) {
            final VisualisationAsset asset = new VisualisationAsset(record.value1(),
                    record.value2(),
                    record.value3() == BYTE_TRUE);
            assets.add(asset);
            LOGGER.info("    Fetching asset: {}", asset);
        }

        return assets;
    }

    /**
     * Utility method to convert a Result into a list of ImportExportAssets.
     */
    private List<ImportExportAsset>
    resultToImportExportAssets(final Result<Record2<String, byte[]>> result) {
        final List<ImportExportAsset> assets = new ArrayList<>(result.size());
        for (final Record2<String, byte[]> record: result) {
            final ImportExportAsset asset = new ByteArrayImportExportAsset(record.value1(), record.value2());
            LOGGER.info("result -> assets: {}", asset);
            assets.add(asset);
        }
        return assets;
    }

    /**
     * Process a path to ensure it has / for queries
     */
    private String slashPath(String in, final boolean isFolder) {
        LOGGER.info("Adding slashes to path '{}'; is folder = {}", in, isFolder);
        if (!in.startsWith("/")) {
            in = "/" + in;
        }

        if (isFolder && !in.endsWith("/")) {
            in = in + "/";
        }

        LOGGER.info("Slashed path is '{}'", in);
        return in;
    }

    /**
     * Adds an entry into the VISUALISATION_ASSETS_UPDATE_DELETE table
     * to indicate that the last operation was a delete, and thus it is safe
     * to wipe all changes if the user wants to save draft to live when draft is empty.
     * @param userUuid The user ID we're operating under
     * @param ownerDocId The ID of the doc that owns these resources.
     * @param txnContext The transaction context for the query.
     */
    private void markUpdateAsDelete(final String userUuid,
                                    final String ownerDocId,
                                    final DSLContext txnContext) {

        // Record the fact that something was deleted so we can allow saveDraftToLive
        final int rowsInserted = txnContext.insertInto(Tables.VISUALISATION_ASSETS_UPDATE_DELETE)
                .columns(Tables.VISUALISATION_ASSETS_UPDATE_DELETE.DRAFT_USER_UUID,
                        Tables.VISUALISATION_ASSETS_UPDATE_DELETE.OWNER_DOC_UUID)
                .values(userUuid, ownerDocId)
                .onDuplicateKeyIgnore()
                .execute();
        LOGGER.info("markUpdateAsDelete: created {} record to flag last operation was delete", rowsInserted);
    }

    /**
     * Deletes entries from the VISUALISATION_ASSETS_UPDATE_DELETE table
     * to indicate that the last operation was not a delete, and thus it is not safe
     * to wipe all changes if the user wants to save draft to live when draft is empty.
     * @param userUuid The user ID we're operating under
     * @param ownerDocId The ID of the doc that owns these resources.
     * @param txnContext The transaction context for the query.
     */
    private void markUpdateAsNotDelete(final String userUuid,
                                       final String ownerDocId,
                                       final DSLContext txnContext) {
        final int rowsDeleted = txnContext.deleteFrom(Tables.VISUALISATION_ASSETS_UPDATE_DELETE)
                .where(Tables.VISUALISATION_ASSETS_UPDATE_DELETE.DRAFT_USER_UUID.eq(userUuid)
                        .and(Tables.VISUALISATION_ASSETS_UPDATE_DELETE.OWNER_DOC_UUID.eq(ownerDocId)))
                .execute();
        LOGGER.info("markUpdateAsNotDelete: deleted {} records to flag that the last operation was not delete",
                rowsDeleted);
    }

    /**
     * Returns whether the last operation was a delete.
     * @param userUuid The user ID we're operating under
     * @param ownerDocId The ID of the doc that owns these resources.
     * @param txnContext The transaction context for the query.
     */
    private boolean isLastUpdateDelete(final String userUuid,
                                       final String ownerDocId,
                                       final DSLContext txnContext) {
        final Result<Record1<Integer>> resultLastUpdateDelete = txnContext
                .selectCount()
                .from(Tables.VISUALISATION_ASSETS_UPDATE_DELETE)
                .where(Tables.VISUALISATION_ASSETS_UPDATE_DELETE.DRAFT_USER_UUID.eq(userUuid)
                        .and(Tables.VISUALISATION_ASSETS_UPDATE_DELETE.OWNER_DOC_UUID.eq(ownerDocId)))
                .fetch();

        final int count = resultLastUpdateDelete.getFirst().value1();
        LOGGER.info("isLastUpdateDelete: is last update delete? Found {} records", count);
        return count > 0;
    }

    /**
     * Utility to populate the draft table from the live table, if necessary.
     * @param userUuid The user ID we're operating under
     * @param ownerDocId The ID of the doc that owns these resources.
     * @param txnContext The transaction context for the query.
     */
    private void populateDraft(final String userUuid,
                               final String ownerDocId,
                               final DSLContext txnContext) throws DataAccessException {

        txnContext.insertInto(Tables.VISUALISATION_ASSETS_DRAFT)
                .columns(Tables.VISUALISATION_ASSETS_DRAFT.DRAFT_USER_UUID,
                        Tables.VISUALISATION_ASSETS_DRAFT.OWNER_DOC_UUID,
                        Tables.VISUALISATION_ASSETS_DRAFT.ASSET_UUID,
                        Tables.VISUALISATION_ASSETS_DRAFT.PATH,
                        Tables.VISUALISATION_ASSETS_DRAFT.PATH_HASH,
                        Tables.VISUALISATION_ASSETS_DRAFT.IS_FOLDER,
                        Tables.VISUALISATION_ASSETS_DRAFT.DATA)
                .select(txnContext.select(DSL.val(userUuid),
                                Tables.VISUALISATION_ASSETS.OWNER_DOC_UUID,
                                Tables.VISUALISATION_ASSETS.ASSET_UUID,
                                Tables.VISUALISATION_ASSETS.PATH,
                                Tables.VISUALISATION_ASSETS.PATH_HASH,
                                Tables.VISUALISATION_ASSETS.IS_FOLDER,
                                Tables.VISUALISATION_ASSETS.DATA)
                        .from(Tables.VISUALISATION_ASSETS)
                        .whereNotExists(
                                txnContext.selectOne()
                                        .from(Tables.VISUALISATION_ASSETS_DRAFT)
                                        .where(Tables.VISUALISATION_ASSETS_DRAFT.DRAFT_USER_UUID.eq(userUuid)
                                                .and(Tables.VISUALISATION_ASSETS_DRAFT.OWNER_DOC_UUID.eq(ownerDocId))
                                        )
                        )
                ).execute();
    }

    @Override
    public VisualisationAssets fetchDraftAssets(final String userUuid,
                                                final String ownerDocId) throws IOException {
        Objects.requireNonNull(userUuid);
        Objects.requireNonNull(ownerDocId);

        LOGGER.info("Fetching assets for user {}, document {}", userUuid, ownerDocId);
        try {
            // Do everything in one transaction
            final List<VisualisationAsset> assets = new ArrayList<>();
            final boolean[] dirty = new boolean[1];
            dirty[0] = true;

            JooqUtil.transaction(connProvider, txnContext -> {
                Result<Record3<String, String, Byte>> result = txnContext
                        .select(Tables.VISUALISATION_ASSETS_DRAFT.ASSET_UUID,
                                Tables.VISUALISATION_ASSETS_DRAFT.PATH,
                                Tables.VISUALISATION_ASSETS_DRAFT.IS_FOLDER)
                        .from(Tables.VISUALISATION_ASSETS_DRAFT)
                        .where(Tables.VISUALISATION_ASSETS_DRAFT.OWNER_DOC_UUID.eq(ownerDocId)
                                .and(Tables.VISUALISATION_ASSETS_DRAFT.DRAFT_USER_UUID.eq(userUuid)))
                        .fetch();

                if (result.isEmpty()) {
                    // No results so try looking in the live table instead
                    LOGGER.info("No draft assets - getting from live table");
                    dirty[0] = false;
                    result = txnContext
                            .select(Tables.VISUALISATION_ASSETS.ASSET_UUID,
                                    Tables.VISUALISATION_ASSETS.PATH,
                                    Tables.VISUALISATION_ASSETS.IS_FOLDER)
                            .from(Tables.VISUALISATION_ASSETS)
                            .where(Tables.VISUALISATION_ASSETS.OWNER_DOC_UUID.eq(ownerDocId))
                            .fetch();
                }
                assets.addAll(resultToAssets(result));
            });
            LOGGER.info("Dirty is {}", dirty[0]);
            return new VisualisationAssets(ownerDocId, dirty[0], null, assets);

        } catch (final DataAccessException e) {
            LOGGER.error("Error fetching draft visualisation assets for user {}, document {}: {}",
                    userUuid, ownerDocId, e.getMessage(), e);
            throw new IOException("Error fetching visualisation assets: " + e.getMessage(), e);
        } catch (final Throwable t) {
            LOGGER.error("Error fetching draft visualisation assets for user {}, document {}: {}",
                    userUuid, ownerDocId, t.getMessage(), t);
            throw t;
        }

}

    @Override
    public void updateNewFolder(final String userUuid,
                                final String ownerDocId,
                                final String path)
            throws IOException, DataAccessException {

        Objects.requireNonNull(userUuid);
        Objects.requireNonNull(ownerDocId);
        Objects.requireNonNull(path);

        try {
            JooqUtil.transaction(connProvider, txnContext -> {
                populateDraft(userUuid, ownerDocId, txnContext);

                final String recordPath = slashPath(path, true);
                final byte[] hashRecordPath =
                        Hashing.sha256().hashString(recordPath, StandardCharsets.UTF_8).asBytes();

                final int rowsInserted = txnContext
                        .insertInto(Tables.VISUALISATION_ASSETS_DRAFT)
                        .columns(Tables.VISUALISATION_ASSETS_DRAFT.DRAFT_USER_UUID,
                                Tables.VISUALISATION_ASSETS_DRAFT.OWNER_DOC_UUID,
                                Tables.VISUALISATION_ASSETS_DRAFT.ASSET_UUID,
                                Tables.VISUALISATION_ASSETS_DRAFT.PATH,
                                Tables.VISUALISATION_ASSETS_DRAFT.PATH_HASH,
                                Tables.VISUALISATION_ASSETS_DRAFT.IS_FOLDER,
                                Tables.VISUALISATION_ASSETS_DRAFT.DATA)
                        .values(userUuid,
                                ownerDocId,
                                UUID.randomUUID().toString(),
                                recordPath,
                                hashRecordPath,
                                BYTE_TRUE,
                                null)
                        .execute();
                if (rowsInserted != 1) {
                    throw new DataAccessException("1 row should have been inserted for '"
                                                  + recordPath + "' but "
                                                  + rowsInserted + " rows were inserted");
                }

                markUpdateAsNotDelete(userUuid, ownerDocId, txnContext);
            });
        } catch (final DataAccessException e) {
            LOGGER.error("Error adding a new folder asset for user {}, document {}: {}",
                    userUuid, ownerDocId, e.getMessage(), e);
            throw new IOException("Error adding a new folder asset: " + e.getMessage(), e);
        } catch (final Throwable t) {
            LOGGER.error("Error adding a new folder asset for user {}, document {}: {}",
                    userUuid, ownerDocId, t.getMessage(), t);
            throw t;
        }
    }

    @Override
    public void updateNewFile(final String userUuid,
                              final String ownerDocId,
                              final String path)
            throws IOException {

        Objects.requireNonNull(userUuid);
        Objects.requireNonNull(ownerDocId);
        Objects.requireNonNull(path);

        try {
            JooqUtil.transaction(connProvider, txnContext -> {
                populateDraft(userUuid, ownerDocId, txnContext);

                final String recordPath = slashPath(path, false);
                final byte[] hashRecordPath =
                        Hashing.sha256().hashString(recordPath, StandardCharsets.UTF_8).asBytes();

                final int rowsInserted = txnContext
                        .insertInto(Tables.VISUALISATION_ASSETS_DRAFT)
                        .columns(Tables.VISUALISATION_ASSETS_DRAFT.DRAFT_USER_UUID,
                                Tables.VISUALISATION_ASSETS_DRAFT.OWNER_DOC_UUID,
                                Tables.VISUALISATION_ASSETS_DRAFT.ASSET_UUID,
                                Tables.VISUALISATION_ASSETS_DRAFT.PATH,
                                Tables.VISUALISATION_ASSETS_DRAFT.PATH_HASH,
                                Tables.VISUALISATION_ASSETS_DRAFT.IS_FOLDER,
                                Tables.VISUALISATION_ASSETS_DRAFT.DATA)
                        .values(userUuid,
                                ownerDocId,
                                UUID.randomUUID().toString(),
                                recordPath,
                                hashRecordPath,
                                BYTE_FALSE,
                                EMPTY_CONTENT)
                        .execute();
                if (rowsInserted != 1) {
                    throw new DataAccessException("1 row should have been inserted for '"
                                                  + recordPath + "' but "
                                                  + rowsInserted + " rows were inserted");
                }

                markUpdateAsNotDelete(userUuid, ownerDocId, txnContext);
            });
        } catch (final DataAccessException e) {
            LOGGER.error("Error adding a new file asset for user {}, document {}: {}",
                    userUuid, ownerDocId, e.getMessage(), e);
            throw new IOException("Error adding a new file asset: " + e.getMessage(), e);
        } catch (final Throwable t) {
            LOGGER.error("Error adding a new file asset for user {}, document {}: {}",
                    userUuid, ownerDocId, t.getMessage(), t);
            throw t;
        }
    }

    @Override
    public void updateNewUploadedFile(final String userUuid,
                                      final String ownerDocId,
                                      final String path,
                                      final InputStream uploadStream)
            throws IOException {

        Objects.requireNonNull(userUuid);
        Objects.requireNonNull(ownerDocId);
        Objects.requireNonNull(path);
        Objects.requireNonNull(uploadStream);

        try {
            // Jooq doesn't support InputStreams so need to use JDBC
            final String INSERT_SQL = "INSERT INTO visualisation_assets_draft ( "
                                      + "draft_user_uuid, owner_doc_uuid, asset_uuid, path, path_hash, is_folder, data"
                                      + " ) values ( "
                                      + "?, ?, ?, ?, ?, ?, ?"
                                      + " )";

            final String recordPath = slashPath(path, false);
            final byte[] hashRecordPath =
                    Hashing.sha256().hashString(recordPath, StandardCharsets.UTF_8).asBytes();
            final String assetUuid = UUID.randomUUID().toString();

            JooqUtil.transaction(connProvider, txnContext -> {
                populateDraft(userUuid, ownerDocId, txnContext);
                txnContext.connection(connection -> {
                    try (final PreparedStatement stmt = connection.prepareStatement(INSERT_SQL)) {
                        stmt.setString(1, userUuid);
                        stmt.setString(2, ownerDocId);
                        stmt.setString(3, assetUuid);
                        stmt.setString(4, recordPath);
                        stmt.setBytes(5, hashRecordPath);
                        stmt.setByte(6, BYTE_FALSE);
                        stmt.setBinaryStream(7, uploadStream);
                        final int rowsInserted = stmt.executeUpdate();
                        if (rowsInserted != 1) {
                            throw new DataAccessException("1 row should have been inserted for "
                                                          + "uploaded content for '" + recordPath
                                                          + "' but " + rowsInserted
                                                          + " rows were inserted");
                        }
                    }
                });

                markUpdateAsNotDelete(userUuid, ownerDocId, txnContext);
            });
        } catch (final DataAccessException e) {
            LOGGER.error("Error adding a new uploaded file asset for user {}, document {}: {}",
                    userUuid, ownerDocId, e.getMessage(), e);
            throw new IOException("Error adding a uploaded new file asset: " + e.getMessage(), e);
        } catch (final Throwable t) {
            LOGGER.error("Error adding a new uploaded file asset for user {}, document {}: {}",
                    userUuid, ownerDocId, t.getMessage(), t);
            throw t;
        }
    }

    @Override
    public void updateRename(final String userUuid,
                             final String ownerDocId,
                             final String oldPath,
                             final String newPath,
                             final boolean isFolder)
            throws IOException {
        Objects.requireNonNull(userUuid);
        Objects.requireNonNull(ownerDocId);
        Objects.requireNonNull(oldPath);
        Objects.requireNonNull(newPath);

        // Make sure values have / before and after the name, except if this refers to a file name
        final String slashedOldPath = slashPath(oldPath, isFolder);
        final String slashedNewPath = slashPath(newPath, isFolder);

        try {
            // This could all be run in one query using variables or Common Table Expressions.
            // However, the resulting Jooq would be completely incomprehensible, even if the underlying
            // SQL is readable. So, in the interests of maintainability, this uses multiple queries.
            // Renames won't be common and will only affect a few files
            // so maximum efficiency isn't required here.
            JooqUtil.transaction(connProvider, txnContext -> {
                populateDraft(userUuid, ownerDocId, txnContext);

                // Get a list of the paths to update
                final Result<Record2<Integer, String>> result = txnContext
                        .select(Tables.VISUALISATION_ASSETS_DRAFT.ID,
                                Tables.VISUALISATION_ASSETS_DRAFT.PATH)
                        .from(Tables.VISUALISATION_ASSETS_DRAFT)
                        .where(Tables.VISUALISATION_ASSETS_DRAFT.DRAFT_USER_UUID.eq(userUuid)
                                .and(Tables.VISUALISATION_ASSETS_DRAFT.OWNER_DOC_UUID.eq(ownerDocId)))
                        .fetch();

                // Update all the paths we've received
                for (final Record2<Integer, String> record : result) {
                    final Integer id = record.value1();
                    final String recordOldPath = record.value2();

                    // Does this path need updating? The old and new paths always start at the root
                    if (recordOldPath.startsWith(slashedOldPath)) {
                        final String recordNewPath = slashedNewPath + recordOldPath.substring(slashedOldPath.length());
                        final byte[] hashedRecordNewPath =
                                Hashing.sha256().hashString(recordNewPath, StandardCharsets.UTF_8).asBytes();
                        final int rowsUpdated = txnContext.update(Tables.VISUALISATION_ASSETS_DRAFT)
                                .set(Tables.VISUALISATION_ASSETS_DRAFT.PATH, recordNewPath)
                                .set(Tables.VISUALISATION_ASSETS_DRAFT.PATH_HASH, hashedRecordNewPath)
                                .where(Tables.VISUALISATION_ASSETS_DRAFT.ID.eq(id))
                                .execute();
                        if (rowsUpdated != 1) {
                            throw new DataAccessException("1 row should have been updated for '"
                                                          + recordOldPath + "' but "
                                                          + rowsUpdated + " rows were updated");
                        }
                    }
                }

                markUpdateAsNotDelete(userUuid, ownerDocId, txnContext);
            });
        } catch (final DataAccessException e) {
            LOGGER.error("Error renaming an asset for user {}, document {}: {}",
                    userUuid, ownerDocId, e.getMessage(), e);
            throw new IOException("Error renaming an asset: " + e.getMessage(), e);
        } catch (final Throwable t) {
            LOGGER.error("Error renaming an asset for user {}, document {}: {}",
                    userUuid, ownerDocId, t.getMessage(), t);
            throw t;
        }
    }

    @Override
    public void updateDelete(final String userUuid,
                             final String ownerDocId,
                             final String path,
                             final boolean isFolder)
            throws IOException {

        Objects.requireNonNull(userUuid);
        Objects.requireNonNull(ownerDocId);
        Objects.requireNonNull(path);

        final String deletedPath = slashPath(path, isFolder);

        try {
            JooqUtil.transaction(connProvider, txnContext -> {
                populateDraft(userUuid, ownerDocId, txnContext);
                final int rowsDeleted = txnContext.deleteFrom(Tables.VISUALISATION_ASSETS_DRAFT)
                        .where(Tables.VISUALISATION_ASSETS_DRAFT.DRAFT_USER_UUID.eq(userUuid)
                                .and(Tables.VISUALISATION_ASSETS_DRAFT.OWNER_DOC_UUID.eq(ownerDocId))
                                .and(DSL.substring(Tables.VISUALISATION_ASSETS_DRAFT.PATH,
                                        DSL.val(START_OF_SUBSTRING), DSL.length(DSL.val(deletedPath))).eq(deletedPath))
                        )
                        .execute();

                // If deleting the root of a tree then more than 1 rows will be deleted
                if (rowsDeleted < 1) {
                    throw new DataAccessException("1 row should have been deleted for '"
                                                  + deletedPath
                                                  + "' but " + rowsDeleted + " rows were deleted.");
                }

                markUpdateAsDelete(userUuid, ownerDocId, txnContext);
            });
        } catch (final DataAccessException e) {
            LOGGER.error("Error deleting an asset for user {}, document {}: {}",
                    userUuid, ownerDocId, e.getMessage(), e);
            throw new IOException("Error deleting an asset: " + e.getMessage(), e);
        } catch (final Throwable t) {
            LOGGER.error("Error deleting an asset for user {}, document {}: {}",
                    userUuid, ownerDocId, t.getMessage(), t);
            throw t;
        }
    }

    @Override
    public void updateContent(final String userUuid,
                              final String ownerDocId,
                              final String path,
                              final byte[] content)
            throws IOException {

        Objects.requireNonNull(userUuid);
        Objects.requireNonNull(ownerDocId);
        Objects.requireNonNull(path);
        Objects.requireNonNull(content);

        final String slashedPath = slashPath(path, false);
        final byte[] pathHash = Hashing.sha256().hashString(slashedPath, StandardCharsets.UTF_8).asBytes();

        try {
            JooqUtil.transaction(connProvider, txnContext -> {
                populateDraft(userUuid, ownerDocId, txnContext);
                final int rowsUpdated = txnContext.update(Tables.VISUALISATION_ASSETS_DRAFT)
                        .set(Tables.VISUALISATION_ASSETS_DRAFT.DATA, content)
                        .where(Tables.VISUALISATION_ASSETS_DRAFT.DRAFT_USER_UUID.eq(userUuid)
                                .and(Tables.VISUALISATION_ASSETS_DRAFT.OWNER_DOC_UUID.eq(ownerDocId))
                                .and(Tables.VISUALISATION_ASSETS_DRAFT.PATH.eq(slashedPath))
                                .and(Tables.VISUALISATION_ASSETS_DRAFT.PATH_HASH.eq(pathHash)))
                        .execute();
                if (rowsUpdated != 1) {
                    throw new DataAccessException("1 row should have been updated: " + rowsUpdated);
                }

                markUpdateAsNotDelete(userUuid, ownerDocId, txnContext);
            });
        } catch (final DataAccessException e) {
            LOGGER.error("Error updating asset content for user {}, document {}: {}",
                    userUuid, ownerDocId, e.getMessage(), e);
            throw new IOException("Error updating asset content: " + e.getMessage(), e);
        } catch (final Throwable t) {
            LOGGER.error("Error updating asset content for user {}, document {}: {}",
                    userUuid, ownerDocId, t.getMessage(), t);
            throw t;
        }
    }

    @Override
    public String getDraftContent(final String userUuid,
                                  final String ownerDocId,
                                  final String path) throws IOException {
        Objects.requireNonNull(userUuid);
        Objects.requireNonNull(ownerDocId);
        Objects.requireNonNull(path);

        final String slashedPath = slashPath(path, false);
        final byte[] pathHash = Hashing.sha256().hashString(slashedPath, StandardCharsets.UTF_8).asBytes();
        final String[] content = new String[1];

        try {
            JooqUtil.transaction(connProvider, txnContext -> {

                final Field<Integer> draftDataLength = DSL.field(
                        "LENGTH({0})",
                        Integer.class,
                        Tables.VISUALISATION_ASSETS_DRAFT.DATA);
                Result<Record1<byte[]>> result = txnContext.select(Tables.VISUALISATION_ASSETS_DRAFT.DATA)
                        .from(Tables.VISUALISATION_ASSETS_DRAFT)
                        .where(Tables.VISUALISATION_ASSETS_DRAFT.DRAFT_USER_UUID.eq(userUuid)
                                .and(Tables.VISUALISATION_ASSETS_DRAFT.OWNER_DOC_UUID.eq(ownerDocId))
                                .and(Tables.VISUALISATION_ASSETS_DRAFT.PATH.eq(slashedPath))
                                .and(Tables.VISUALISATION_ASSETS_DRAFT.PATH_HASH.eq(pathHash))
                                .and(draftDataLength.lt(MAX_EDITABLE_CONTENT_LENGTH)))
                        .fetch();
                if (result.isEmpty()) {
                    LOGGER.info("No results for getting draft content for {}; trying live table", path);
                    final Field<Integer> liveDataLength = DSL.field(
                            "LENGTH({0})",
                            Integer.class,
                            Tables.VISUALISATION_ASSETS.DATA);
                    result = txnContext.select(Tables.VISUALISATION_ASSETS.DATA)
                            .from(Tables.VISUALISATION_ASSETS)
                            .where(Tables.VISUALISATION_ASSETS.OWNER_DOC_UUID.eq(ownerDocId)
                                    .and(Tables.VISUALISATION_ASSETS.PATH.eq(slashedPath))
                                    .and(Tables.VISUALISATION_ASSETS.PATH_HASH.eq(pathHash))
                                    .and(liveDataLength.lt(MAX_EDITABLE_CONTENT_LENGTH)))
                            .fetch();
                }

                if (result.isEmpty()) {
                    content[0] = null;
                } else {
                    try {
                        content[0] = decodeToUtf8String(result.getFirst().value1());
                    } catch (final CharacterCodingException e) {
                        // We expect errors here as things like PNG cannot be converted to UTF-8 Strings.
                        LOGGER.info("Cannot convert asset data '{}' to UTF-8: {}", path, e.getMessage());
                        content[0] = null;
                    }
                }
            });
        } catch (final DataAccessException e) {
            LOGGER.error("Error getting draft asset content for user {}, document {}: {}",
                    userUuid, ownerDocId, e.getMessage(), e);
            throw new IOException("Error getting draft asset content: " + e.getMessage(), e);
        } catch (final Throwable t) {
            LOGGER.error("Error getting draft asset content for user {}, document {}: {}",
                    userUuid, ownerDocId, t.getMessage(), t);
            throw t;
        }

        return content[0];
    }

    /**
     * Method to try to convert the given byte array to UTF-8 String, throwing an error if
     * the string cannot be converted.
     * @param input The byte array to convert.
     * @return The input as a String
     * @throws CharacterCodingException if the data cannot be converted to UTF-8 string.
     */
    private String decodeToUtf8String(final byte[] input)
            throws CharacterCodingException {

        final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);

        final CharBuffer buf = CharBuffer.allocate(input.length);
        final CoderResult result = decoder.decode(ByteBuffer.wrap(input), buf, true);
        if (result.isError()) {
            result.throwException();
        }
        decoder.flush(buf);
        buf.flip(); // Set positions for reading
        return buf.toString();
    }

    @Override
    public void saveDraftToLive(final String userUuid, final String ownerDocId) throws IOException {
        LOGGER.info("saveDraftToLive({}, {})", userUuid, ownerDocId);

        Objects.requireNonNull(userUuid);
        Objects.requireNonNull(ownerDocId);

        // Timestamp
        final long timestamp = Instant.now().toEpochMilli();

        // This could probably be more efficient to avoid copying lots of data when it hasn't changed
        // However this version works, and it all happens inside the DB, so shouldn't be too bad.

        try {
            // Do everything in one transaction
            JooqUtil.transaction(connProvider, txnContext -> {

                // If the user somehow manages to send saveDraftToLive() twice they can delete
                // all their data. So we only allow copying if either the draft table has some records
                // or if the last operation was a delete.
                boolean canCopyDraftToLive = isLastUpdateDelete(userUuid, ownerDocId, txnContext);

                if (!canCopyDraftToLive) {
                    // Last operation wasn't delete, so check if there is data in draft to copy to live
                    final Result<Record1<Integer>> result = txnContext
                            .selectCount()
                            .from(Tables.VISUALISATION_ASSETS_DRAFT)
                            .where(Tables.VISUALISATION_ASSETS_DRAFT.DRAFT_USER_UUID.eq(userUuid)
                                    .and(Tables.VISUALISATION_ASSETS_DRAFT.OWNER_DOC_UUID.eq(ownerDocId)))
                            .fetch();
                    final int updateDeleteRecordCount = result.getFirst().value1();
                    if (updateDeleteRecordCount > 0) {
                        LOGGER.info("Can copy draft table to live table: {} updateDelete records exist",
                                updateDeleteRecordCount);
                        canCopyDraftToLive = true;
                    } else {
                        LOGGER.info("Cannot copy draft table to live table; no draft records exist "
                        + "and the last update was not a delete.");
                    }
                }

                if (canCopyDraftToLive) {
                    LOGGER.info("Deleting existing live assets");
                    // Delete all existing live content for the owning document ID
                    int recordCount = txnContext
                            .deleteFrom(Tables.VISUALISATION_ASSETS)
                            .where(Tables.VISUALISATION_ASSETS.OWNER_DOC_UUID.eq(ownerDocId))
                            .execute();
                    LOGGER.info("{} records deleted in live", recordCount);

                    LOGGER.info("Copying data into Live");
                    // Copy all relevant data from the user draft table into the live table
                    recordCount = txnContext
                            .insertInto(Tables.VISUALISATION_ASSETS,
                                    Tables.VISUALISATION_ASSETS.MODIFIED,
                                    Tables.VISUALISATION_ASSETS.OWNER_DOC_UUID,
                                    Tables.VISUALISATION_ASSETS.ASSET_UUID,
                                    Tables.VISUALISATION_ASSETS.PATH,
                                    Tables.VISUALISATION_ASSETS.PATH_HASH,
                                    Tables.VISUALISATION_ASSETS.IS_FOLDER,
                                    Tables.VISUALISATION_ASSETS.DATA)
                            .select(txnContext.select(
                                            DSL.val(timestamp),
                                            Tables.VISUALISATION_ASSETS_DRAFT.OWNER_DOC_UUID,
                                            Tables.VISUALISATION_ASSETS_DRAFT.ASSET_UUID,
                                            Tables.VISUALISATION_ASSETS_DRAFT.PATH,
                                            Tables.VISUALISATION_ASSETS_DRAFT.PATH_HASH,
                                            Tables.VISUALISATION_ASSETS_DRAFT.IS_FOLDER,
                                            Tables.VISUALISATION_ASSETS_DRAFT.DATA)
                                    .from(Tables.VISUALISATION_ASSETS_DRAFT)
                                    .where(Tables.VISUALISATION_ASSETS_DRAFT.DRAFT_USER_UUID.eq(userUuid)
                                            .and(Tables.VISUALISATION_ASSETS_DRAFT.OWNER_DOC_UUID.eq(ownerDocId))))
                            .execute();
                    LOGGER.info("Copied {} records from draft into Live", recordCount);

                    // Delete everything in the draft table so next time we'll get clean live data
                    recordCount = txnContext
                            .deleteFrom(Tables.VISUALISATION_ASSETS_DRAFT)
                            .where(Tables.VISUALISATION_ASSETS_DRAFT.DRAFT_USER_UUID.eq(userUuid)
                                    .and(Tables.VISUALISATION_ASSETS_DRAFT.OWNER_DOC_UUID.eq(ownerDocId)))
                            .execute();
                    LOGGER.info("Deleted {} records in the draft table", recordCount);

                    markUpdateAsNotDelete(userUuid, ownerDocId, txnContext);
                }
            });
        } catch (final DataAccessException e) {
            LOGGER.error("Error saving draft assets to live for user {}, doc {}: {}",
                    userUuid, ownerDocId, e.getMessage(), e);
            throw new IOException("Error saving draft assets to live: " + e.getMessage(), e);
        } catch (final Throwable t) {
            LOGGER.error("Error saving draft assets to live for user {}, doc {}: {}",
                    userUuid, ownerDocId, t.getMessage(), t);
            throw t;
        }
    }

    @Override
    public void revertDraftFromLive(final String userUuid, final String ownerDocId) throws IOException {

        Objects.requireNonNull(userUuid);
        Objects.requireNonNull(ownerDocId);

        LOGGER.info("revertDraftFromLive()");
        try {
            JooqUtil.transaction(connProvider, txnContext -> {
                txnContext
                        .deleteFrom(Tables.VISUALISATION_ASSETS_DRAFT)
                        .where(Tables.VISUALISATION_ASSETS_DRAFT.DRAFT_USER_UUID.eq(userUuid)
                                .and(Tables.VISUALISATION_ASSETS_DRAFT.OWNER_DOC_UUID.eq(ownerDocId)))
                        .execute();

                markUpdateAsNotDelete(userUuid, ownerDocId, txnContext);
            });
        } catch (final DataAccessException e) {
            LOGGER.error("Error reverting draft from live: {}", e.getMessage(), e);
            throw new IOException("Error reverting draft: " + e.getMessage(), e);
        } catch (final Throwable t) {
            LOGGER.error("Error reverting draft from live for user {}, doc {}: {}",
                    userUuid, ownerDocId, t.getMessage(), t);
            throw t;
        }
    }

    @Override
    public List<ImportExportAsset> getAssetsForExport(final String ownerDocId) throws IOException {
        Objects.requireNonNull(ownerDocId);

        LOGGER.info("Getting export assets for document {}", ownerDocId);
        try {
            final Result<Record2<String, byte[]>> result =
                    JooqUtil.contextResult(connProvider, context -> context
                            .select(Tables.VISUALISATION_ASSETS.PATH,
                                    Tables.VISUALISATION_ASSETS.DATA)
                            .from(Tables.VISUALISATION_ASSETS)
                            .where(Tables.VISUALISATION_ASSETS.OWNER_DOC_UUID.eq(ownerDocId))
                            .fetch());
            return resultToImportExportAssets(result);
        } catch (final DataAccessException e) {
            LOGGER.error("Error getting export assets for document '{}': {}",
                    ownerDocId, e.getMessage(), e);
            throw new IOException("Error getting export assets for document: " + e.getMessage(), e);
        } catch (final Throwable t) {
            LOGGER.error("Error getting export assets for document '{}': {}",
                    ownerDocId, t.getMessage(), t);
            throw t;
        }
    }

    @Override
    public void setAssetsFromImport(final String ownerDocId, final Collection<ImportExportAsset> pathAssets)
            throws IOException {
        Objects.requireNonNull(ownerDocId);
        Objects.requireNonNull(pathAssets);

        LOGGER.info("Setting import assets for document {}", ownerDocId);

        final long timestamp = Instant.now().toEpochMilli();

        try {
            JooqUtil.transaction(connProvider, txnContext -> {
                try {
                    // Delete all existing live content for the owning document ID
                    final int recordCount = txnContext
                            .deleteFrom(Tables.VISUALISATION_ASSETS)
                            .where(Tables.VISUALISATION_ASSETS.OWNER_DOC_UUID.eq(ownerDocId))
                            .execute();
                    LOGGER.info("{} records deleted in live before import", recordCount);

                    for (final ImportExportAsset asset : pathAssets) {
                        final String assetUuid = UUID.randomUUID().toString();
                        final String path = asset.getKey();
                        final byte[] pathHash = Hashing.sha256().hashString(path, StandardCharsets.UTF_8).asBytes();
                        final byte[] data = asset.getInputData();
                        final boolean isFolder = data == null;

                        txnContext
                                .insertInto(Tables.VISUALISATION_ASSETS,
                                        Tables.VISUALISATION_ASSETS.MODIFIED,
                                        Tables.VISUALISATION_ASSETS.OWNER_DOC_UUID,
                                        Tables.VISUALISATION_ASSETS.ASSET_UUID,
                                        Tables.VISUALISATION_ASSETS.PATH,
                                        Tables.VISUALISATION_ASSETS.PATH_HASH,
                                        Tables.VISUALISATION_ASSETS.DATA,
                                        Tables.VISUALISATION_ASSETS.IS_FOLDER)
                                .values(timestamp,
                                        ownerDocId,
                                        assetUuid,
                                        path,
                                        pathHash,
                                        data,
                                        isFolder
                                                ? BYTE_TRUE
                                                : BYTE_FALSE)
                                .execute();
                    }
                } catch (final IOException e) {
                    throw new DataAccessException("Error reading data from asset: " + e.getMessage(), e);
                }
            });
        } catch (final DataAccessException e) {
            LOGGER.error("Error setting import assets for document '{}': {}",
                    ownerDocId, e.getMessage(), e);
            throw new IOException("Error setting import assets for document: " + e.getMessage(), e);
        } catch (final Throwable t) {
            LOGGER.error("Error setting import assets for document '{}': {}",
                    ownerDocId, t.getMessage(), t);
            throw t;
        }
    }

    @Override
    public Instant writeLiveToServletCache(final String tempFilePrefix,
                                           final String tempFileSuffix,
                                           final String ownerDocId,
                                           final String assetPath,
                                           final Instant cacheTimestamp,
                                           final Path cachedPath) throws IOException {

        Objects.requireNonNull(ownerDocId);
        Objects.requireNonNull(assetPath);
        Objects.requireNonNull(cacheTimestamp);
        Objects.requireNonNull(cachedPath);

        try {
            // Using JDBC so we can use InputStream to get file contents
            final String SELECT_SQL = "SELECT modified, data "
                                      + "FROM visualisation_assets "
                                      + "WHERE modified > ? AND owner_doc_uuid = ? AND path = ? AND path_hash = ?";

            final String recordPath = slashPath(assetPath, false);
            final byte[] hashRecordPath =
                    Hashing.sha256().hashString(recordPath, StandardCharsets.UTF_8).asBytes();
            final Instant[] dbTimestamp = new Instant[1];
            dbTimestamp[0] = null;

            JooqUtil.transaction(connProvider, txnContext -> {
                txnContext.connection(connection -> {
                    try (final PreparedStatement stmt = connection.prepareStatement(SELECT_SQL)) {
                        stmt.setLong(1, cacheTimestamp.toEpochMilli());
                        stmt.setString(2, ownerDocId);
                        stmt.setString(3, recordPath);
                        stmt.setBytes(4, hashRecordPath);
                        final ResultSet resultSet = stmt.executeQuery();
                        if (resultSet.next()) {
                            // Got data
                            final long epochMillis = resultSet.getLong(1);
                            LOGGER.info("Got epochMillis: {}", epochMillis);
                            dbTimestamp[0] = Instant.ofEpochMilli(epochMillis);
                            try (final InputStream dataStream = resultSet.getBinaryStream(2)) {
                                saveDataSafely(tempFilePrefix,
                                        tempFileSuffix,
                                        cachedPath,
                                        dataStream);
                            }
                        } else {
                            // No result - either doesn't exist or cache is valid
                            LOGGER.info("No result found for {}:{}", ownerDocId, assetPath);
                        }
                    }
                });
            });

            return dbTimestamp[0];
        } catch (final DataAccessException e) {
            LOGGER.error("Error writing asset data to Servlet cache: {}",
                    e.getMessage(), e);
            throw new IOException("Error writing asset data to Servlet cache: " + e.getMessage(), e);
        } catch (final Throwable t) {
            LOGGER.error("Error writing asset data to Servlet cache: {}", t.getMessage(), t);
            throw t;
        }

    }

    @Override
    public void copyLiveAssets(final String fromDocId, final String toDocId)
            throws IOException {
        try {
            JooqUtil.transaction(connProvider, txnContext -> {
                final Table<Record5<Long, String, byte[], Byte, byte[]>> tmp =
                        txnContext.select(Tables.VISUALISATION_ASSETS.MODIFIED,
                                        Tables.VISUALISATION_ASSETS.PATH,
                                        Tables.VISUALISATION_ASSETS.PATH_HASH,
                                        Tables.VISUALISATION_ASSETS.IS_FOLDER,
                                        Tables.VISUALISATION_ASSETS.DATA)
                                .from(Tables.VISUALISATION_ASSETS)
                                .where(Tables.VISUALISATION_ASSETS.OWNER_DOC_UUID.eq(fromDocId))
                                .asTable("tmp");

                txnContext.insertInto(Tables.VISUALISATION_ASSETS,
                                Tables.VISUALISATION_ASSETS.MODIFIED,
                                Tables.VISUALISATION_ASSETS.OWNER_DOC_UUID,
                                Tables.VISUALISATION_ASSETS.ASSET_UUID,
                                Tables.VISUALISATION_ASSETS.PATH,
                                Tables.VISUALISATION_ASSETS.PATH_HASH,
                                Tables.VISUALISATION_ASSETS.IS_FOLDER,
                                Tables.VISUALISATION_ASSETS.DATA)
                        .select(
                                txnContext.select(tmp.field(Tables.VISUALISATION_ASSETS.MODIFIED),
                                                DSL.val(toDocId),
                                                DSL.uuid().cast(String.class),
                                                tmp.field(Tables.VISUALISATION_ASSETS.PATH),
                                                tmp.field(Tables.VISUALISATION_ASSETS.PATH_HASH),
                                                tmp.field(Tables.VISUALISATION_ASSETS.IS_FOLDER),
                                                tmp.field(Tables.VISUALISATION_ASSETS.DATA))
                                        .from(tmp)
                        )
                        .execute();
            });
        } catch (final DataAccessException e) {
            LOGGER.error("Error copying assets from document '{}' to document '{}': {}",
                    fromDocId, toDocId, e.getMessage(), e);
            throw new IOException("Error copying assets from document '" + fromDocId
                                  + "' to document '" + toDocId + "': " + e.getMessage(), e);
        } catch (final Throwable t) {
            LOGGER.error("Error copying assets from document '{}' to document '{}': {}",
                    fromDocId, toDocId, t.getMessage(), t);
            throw t;
        }
    }

    @Override
    public void deleteAssetsForDoc(final String ownerDocId) throws IOException {
        Objects.requireNonNull(ownerDocId);
        try {
            JooqUtil.transaction(connProvider, txnContext -> {
                txnContext.deleteFrom(Tables.VISUALISATION_ASSETS)
                        .where(Tables.VISUALISATION_ASSETS.OWNER_DOC_UUID.eq(ownerDocId))
                        .execute();
                txnContext.deleteFrom(Tables.VISUALISATION_ASSETS_DRAFT)
                        .where(Tables.VISUALISATION_ASSETS_DRAFT.OWNER_DOC_UUID.eq(ownerDocId))
                        .execute();
            });
        } catch (final DataAccessException e) {
            LOGGER.error("Error deleting assets for document '{}': {}",
                    ownerDocId, e.getMessage(), e);
            throw new IOException("Error deleing assets for document '" + ownerDocId
                                  + "': " + e.getMessage(), e);
        } catch (final Throwable t) {
            LOGGER.error("Error deleting assets for document '{}': {}",
                    ownerDocId, t.getMessage(), t);
            throw t;
        }
    }

    /**
     * Performs a safe write to file, so if the system crashes half-way through writing
     * we don't have a corrupted version of the file.
     * @param tempFilePrefix The prefix for the temporary file we'll create.
     *                       Needed so temporary files can be cleaned up if necessary.
     * @param tempFileSuffix The suffix for the temporary file we'll create.
     *                       Needed so temporary files can be cleaned up if necessary.
     * @param filePath The path to the file we want to create.
     * @param dataStream The data to write to the file. Might be null.
     * @throws IOException If something goes wrong.
     */
    private void saveDataSafely(final String tempFilePrefix,
                                final String tempFileSuffix,
                                final Path filePath,
                                final InputStream dataStream) throws IOException {

        final Path fileDir = filePath.getParent();
        Files.createDirectories(fileDir);
        final Path tempFilePath = Files.createTempFile(fileDir,
                tempFilePrefix,
                tempFileSuffix);

        if (dataStream != null) {
            final long bytesCopied = Files.copy(dataStream, tempFilePath, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Copied {} bytes to path '{}'", bytesCopied, filePath);
        }

        Files.move(tempFilePath,
                filePath,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        LOGGER.info("Copied to '{}': size is now {}", filePath, filePath.toFile().length());
    }

}
