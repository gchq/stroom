package stroom.dashboard.impl.visualisation;

import stroom.importexport.api.ImportExportAsset;
import stroom.visualisation.shared.VisualisationAssets;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Provides a way to store visualisation assets within the database.
 */
public interface VisualisationAssetDao {

    /**
     * Returns all the assets for a given docRef.
     * @param userUuid The user ID that we want draft info for
     * @param ownerId The document that owns the assets
     * @return Assets to display in UI.
     * @throws IOException If something goes wrong.
     */
    VisualisationAssets fetchDraftAssets(String userUuid,
                                         String ownerId) throws IOException;

    /**
     * Creates a new folder.
     * The updates are stored in the Draft table and made live by calling saveDraftToLive().
     * @param userUuid The user ID that is doing the update
     * @param ownerDocId The document that owns the assets
     * @param path The path to the folder, including the folder name.
     * @throws IOException If something goes wrong.
     */
    void updateNewFolder(String userUuid,
                         String ownerDocId,
                         String path)
            throws IOException;

    /**
     * Creates a new file.
     * The updates are stored in the Draft table and made live by calling saveDraftToLive().
     * @param userUuid The user ID that is doing the update
     * @param ownerDocId The document that owns the assets
     * @param path The path to the file, including the file name and extension.
     * @throws IOException If something goes wrong.
     */
    void updateNewFile(String userUuid,
                       String ownerDocId,
                       String path)
            throws IOException;

    /**
     * Creates a new file from a file upload.
     * The updates are stored in the Draft table and made live by calling saveDraftToLive().
     * @param userUuid The user ID that is doing the update
     * @param ownerDocId The document that owns the assets
     * @param path The path to the file, including the file name and extension.
     * @param uploadStream The stream to read the file contents from.
     *                     Must not be null.
     * @throws IOException If something goes wrong.
     */
    void updateNewUploadedFile(String userUuid,
                               String ownerDocId,
                               String path,
                               InputStream uploadStream)
            throws IOException;

    /**
     * Deletes a folder or file.
     * The updates are stored in the Draft table and made live by calling saveDraftToLive().
     * @param userUuid The user ID that is doing the update
     * @param ownerDocId The document that owns the assets
     * @param path The path to the folder or file to be deleted, including the item name.
     * @param isFolder Whether the thing being deleted is a file or folder.
     * @throws IOException If something goes wrong.
     */
    void updateDelete(String userUuid,
                      String ownerDocId,
                      String path,
                      boolean isFolder)
            throws IOException;

    /**
     * Renames a file or folder.
     * The updates are stored in the Draft table and made live by calling saveDraftToLive().
     * @param userUuid The user ID that is doing the update
     * @param ownerDocId The document that owns the assets
     * @param oldPath The existing path to the folder or file, including the item name.
     * @param newPath What the path needs to be changed to.
     * @param isFolder true if the thing being renamed is a folder, false if it is a file.
     * @throws IOException If something goes wrong.
     */
    void updateRename(String userUuid,
                      String ownerDocId,
                      String oldPath,
                      String newPath,
                      boolean isFolder)
            throws IOException;

    /**
     * Updates the content in a file.
     * The updates are stored in the Draft table and made live by calling saveDraftToLive().
     * @param userUuid The user ID that is doing the update
     * @param ownerDocId The document that owns the assets
     * @param path The path to the file, including the file name and extension.
     * @param content The new content for the file.
     * @throws IOException If something goes wrong.
     */
    void updateContent(String userUuid,
                       String ownerDocId,
                       String path,
                       byte[] content)
            throws IOException;

    /**
     * Returns the draft content of a text file as a String for editing
     * in the UI.
     * @param userUuid The user ID that is doing the update
     * @param ownerDocId The document that owns the assets
     * @param path The path to the file, including the file name and extension.
     * @return The text content, or null if the file isn't text.
     * @throws IOException if something goes wrong.
     */
    String getDraftContent(String userUuid,
                           String ownerDocId,
                           String path)
            throws IOException;

    /**
     * Copies all draft information into the main storage so it is live.
     * @param userUuid The user to copy draft information for.
     * @param documentId The document ID that owns the draft information.
     * @throws IOException If something goes wrong.
     */
    void saveDraftToLive(String userUuid, String documentId) throws IOException;

    /**
     * Empties the draft data so fetchDraftAssets() will return the Live data again.
     * @param userUuid Username to revert draft data for.
     * @param documentId The document ID that owns the draft information.
     * @throws IOException If something goes wrong.
     */
    void revertDraftFromLive(String userUuid, String documentId) throws IOException;

    /**
     * Copies all assets - draft and live - from one documentID to another.
     * Can also update any content at the same time.
     * Used when clicking SaveAs in the UI.
     * @param userUuid The user ID who will own any draft assets in the copy
     * @param fromDocumentId The document ID before the save.
     * @param toDocumentId The document ID we're saving to.
     * @param updatedContentPath The path to the updated content, if any. Null if no such content.
     * @param updatedContent Any content in the UI that needs saving too. Null if no such content.
     * @throws IOException If something goes wrong.
     */
    void saveAs(String userUuid,
                String fromDocumentId,
                String toDocumentId,
                String updatedContentPath,
                byte[] updatedContent) throws IOException;

    /**
     * Returns live assets for serialising the assets for a document ID.
     * @param ownerId The document that owns the assets
     * @return ImportExportAssets holding the relevant Import/Export data.
     * @throws IOException If something goes wrong.
     */
    List<ImportExportAsset> getAssetsForExport(String ownerId) throws IOException;

    /**
     * Imports live assets during import. Called from VisualisationStoreImpl.
     * @param ownerId The ID of the document that owns these assets.
     * @param pathAssets The assets that are stored under paths during import/export.
     * @throws IOException If something goes wrong.
     */
    void setAssetsFromImport(String ownerId, Collection<ImportExportAsset> pathAssets) throws IOException;

    /**
     * Gets the data for a given asset.
     * @param tempFilePrefix The prefix for the temporary file we'll create.
     *                       Needed so temporary files can be cleaned up if necessary.
     * @param tempFileSuffix The suffix for the temporary file we'll create.
     *                       Needed so temporary files can be cleaned up if necessary.
     * @param ownerDocId The ID of the owner document we want the data for.
     * @param assetPath The path of the asset within the tree.
     * @param cacheTimestamp The timestamp of the file in the cache. We're only
     *                       interested in files that are later than this.
     * @param cachedPath The path to the file that we want in the
     *                   VisualisationAssetServlet cache. This method will write
     *                   the file content to the cached path, if the data in the
     *                   database is after the cacheTimestamp.
     * @return If the file is written then returns the latest DB timestamp.
     *         Otherwise, returns null.
     * @throws IOException if something goes wrong.
     */
    Instant writeLiveToServletCache(
            String tempFilePrefix,
            String tempFileSuffix,
            String ownerDocId,
            String assetPath,
            Instant cacheTimestamp,
            Path cachedPath) throws IOException;

    /**
     * Copies assets from one docRef to another.
     * Does not delete assets in the destination. Will throw an error if assets already exist.
     * @param fromDocId Where the assets are copied from
     * @param toDocId Where the assets are going to end up
     * @throws IOException If something goes wrong.
     */
    void copyLiveAssets(String fromDocId, String toDocId) throws IOException;

    /**
     * Deletes all the assets associated with the given document ID.
     * Also deletes all the assets in the draft table to avoid orphaned rows. This will cause
     * strange errors if another user is editing these assets.
     * @param ownerDocId The document that owns the assets.
     * @throws IOException If something goes wrong.
     */
    void deleteAssetsForDoc(String ownerDocId) throws IOException;

    /**
     * Determines whether an asset named '/index.html' exists.
     * @param ownerDocId The document that owns the assets.
     * @return true if the asset exists; false if not.
     */
    boolean indexAssetExists(String ownerDocId) throws IOException;

}
