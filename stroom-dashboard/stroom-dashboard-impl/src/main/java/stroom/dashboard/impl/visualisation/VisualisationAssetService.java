package stroom.dashboard.impl.visualisation;

import stroom.docref.DocRef;
import stroom.importexport.api.ImportExportAsset;
import stroom.resource.api.ResourceStore;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResourceKey;
import stroom.visualisation.shared.VisualisationAssets;
import stroom.visualisation.shared.VisualisationDoc;

import com.google.inject.Inject;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;

/**
 * Intermediates between VisualisationAssetResource and VisualisationAssetDao.
 * Primarily responsible for checking permissions.
 * Allows easy access to Assets within the database.
 */
public class VisualisationAssetService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(VisualisationAssetService.class);

    /** DAO to talk to the DB */
    private final VisualisationAssetDao dao;

    /** Allows access to uploaded files */
    private final ResourceStore resourceStore;

    /** Security checks */
    private final SecurityContext securityContext;

    @SuppressWarnings("unused")
    @Inject
    public VisualisationAssetService(final VisualisationAssetDao dao,
                                     final ResourceStore resourceStore,
                                     final SecurityContext securityContext) {
        this.dao = dao;
        this.resourceStore = resourceStore;
        this.securityContext = securityContext;
    }

    /**
     * Used by the UI to get all the asset metadata associated with a document.
     * @param ownerId The ID of the document that owns these assets.
     * @return An object that holds all the metadata about the assets. Note that
     *         getUploadedFiles() will always return an empty map.
     *         This will be the draft assets for the user logged in.
     * @throws IOException if something goes wrong.
     */
    VisualisationAssets fetchDraftAssets(final String ownerId) throws IOException {
        final DocRef docRef = new DocRef(VisualisationDoc.TYPE, ownerId);
        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
            return dao.fetchDraftAssets(securityContext.getUserRef().getUuid(), ownerId);
        } else {
            // No permission so return empty assets
            LOGGER.warn("User does not have permission to see assets");
            return new VisualisationAssets(ownerId);
        }
    }

    /**
     * Creates a new folder at the given path.
     * @param ownerDocId Document that owns the assets. Must not be null.
     * @param path Path and name of the new file. Must not be null.
     * @throws IOException If something goes wrong.
     */
    void updateNewFolder(final String ownerDocId, final String path) throws IOException {
        Objects.requireNonNull(ownerDocId);
        Objects.requireNonNull(path);

        final DocRef docRef = new DocRef(VisualisationDoc.TYPE, ownerDocId);
        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)) {
            dao.updateNewFolder(
                    securityContext.getUserRef().getUuid(),
                    ownerDocId,
                    path);
        } else {
            LOGGER.warn("User does not have permission to create a new folder '{}'", path);
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to edit this asset");
        }

    }

    /**
     * Creates a new file at the given path.
     * @param ownerDocId Document that owns the assets. Must not be null.
     * @param path Path and name of the new file. Must not be null.
     * @throws IOException If something goes wrong.
     */
    void updateNewFile(final String ownerDocId,
                       final String path) throws IOException {
        Objects.requireNonNull(ownerDocId);
        Objects.requireNonNull(path);

        final DocRef docRef = new DocRef(VisualisationDoc.TYPE, ownerDocId);
        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)) {
            dao.updateNewFile(
                    securityContext.getUserRef().getUuid(),
                    ownerDocId,
                    path);
        } else {
            LOGGER.warn("User does not have permission to create a new file '{}'", path);
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to edit this asset");
        }
    }

    /**
     * Creates a new file at the given path from a file upload.
     * @param ownerDocId Document that owns the assets. Must not be null.
     * @param path Path and name of the new file. Must not be null.
     * @param resourceKey The resourceKey associated with the upload. Must not be null.
     * @throws IOException If something goes wrong.
     */
    void updateNewUploadedFile(final String ownerDocId,
                               final String path,
                               final ResourceKey resourceKey) throws IOException {
        Objects.requireNonNull(ownerDocId);
        Objects.requireNonNull(path);
        Objects.requireNonNull(resourceKey);

        final DocRef docRef = new DocRef(VisualisationDoc.TYPE, ownerDocId);
        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)) {
            final Path uploadPath = resourceStore.getTempFile(resourceKey);
            if (!uploadPath.toFile().exists()) {
                throw new IOException("The uploaded file does not exist");
            }
            // Stream the data into the database from the temp file
            try (final InputStream uploadStream = new BufferedInputStream(new FileInputStream(uploadPath.toFile()))) {
                dao.updateNewUploadedFile(
                        securityContext.getUserRef().getUuid(),
                        ownerDocId,
                        path,
                        uploadStream);
                resourceStore.deleteTempFile(resourceKey);
            }
        } else {
            LOGGER.warn("User does not have permission to create a new file from an upload: '{}'", path);
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to edit this asset");
        }
    }

    /**
     * Deletes a file or folder at the given path, and everything underneath that path.
     * @param ownerDocId Document that owns the assets. Must not be null.
     * @param path Path and name of the file or folder to delete. Must not be null.
     * @param isFolder Whether the thing to delete is a file or folder.
     */
    void updateDelete(final String ownerDocId,
                      final String path,
                      final boolean isFolder) throws IOException {
        Objects.requireNonNull(ownerDocId);
        Objects.requireNonNull(path);

        final DocRef docRef = new DocRef(VisualisationDoc.TYPE, ownerDocId);
        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)) {
            dao.updateDelete(
                    securityContext.getUserRef().getUuid(),
                    ownerDocId,
                    path,
                    isFolder);
        } else {
            LOGGER.warn("User does not have permission to delete an item '{}'", path);
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to edit this asset");
        }
    }

    /**
     * Renames a file or folder at the oldPath.
     * @param ownerDocId Document that owns the assets. Must not be null.
     * @param oldPath Where the thing used to be.
     * @param newPath Where the thing needs to be.
     * @param isFolder true if the thing is a folder, false if it is a file.
     */
    void updateRename(final String ownerDocId,
                      final String oldPath,
                      final String newPath,
                      final boolean isFolder) throws IOException {
        Objects.requireNonNull(ownerDocId);
        Objects.requireNonNull(oldPath);
        Objects.requireNonNull(newPath);

        final DocRef docRef = new DocRef(VisualisationDoc.TYPE, ownerDocId);
        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)) {
            dao.updateRename(
                    securityContext.getUserRef().getUuid(),
                    ownerDocId,
                    oldPath,
                    newPath,
                    isFolder);
        } else {
            LOGGER.warn("User does not have permission to rename an item '{}'", oldPath);
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to edit this asset");
        }
    }

    /**
     * Updates the content in a file.
     * @param ownerDocId Document that owns the assets. Must not be null.
     * @param path Location of the document to update the content for.
     *
     */
    void updateContent(final String ownerDocId,
                       final String path,
                       final byte[] content) throws IOException {
        Objects.requireNonNull(ownerDocId);
        Objects.requireNonNull(path);

        final DocRef docRef = new DocRef(VisualisationDoc.TYPE, ownerDocId);
        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)) {
            dao.updateContent(
                    securityContext.getUserRef().getUuid(),
                    ownerDocId,
                    path,
                    content);
        } else {
            LOGGER.warn("User does not have permission to update the content of an item '{}'", path);
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to edit this asset");
        }
    }

    /**
     * Returns the content of a text file for editing in the UI.
     * Will not return anything if the file isn't a text file.
     * @param ownerDocId Document that owns the assets. Must not be null.
     * @param path Location of the document to update the content for.
     * @return The content, or null if the content cannot be viewed.
     */
    String getDraftContent(final String ownerDocId,
                           final String path)
            throws IOException {

        Objects.requireNonNull(ownerDocId);
        Objects.requireNonNull(path);

        final DocRef docRef = new DocRef(VisualisationDoc.TYPE, ownerDocId);
        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
            return dao.getDraftContent(securityContext.getUserRef().getUuid(),
                    ownerDocId,
                    path);
        } else {
            LOGGER.warn("User does not have permission to view the content of an item");
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to view this asset");
        }
    }

    /**
     * Copies all draft information into the main storage so it is live.
     * @param ownerDocId The document that owns these assets.
     * @throws IOException If something goes wrong.
     */
    public void saveDraftToLive(final String ownerDocId) throws IOException {
        LOGGER.info("saveDraftToLive: {}", ownerDocId);

        final DocRef docRef = new DocRef(VisualisationDoc.TYPE, ownerDocId);
        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)) {
            dao.saveDraftToLive(securityContext.getUserRef().getUuid(), ownerDocId);
        } else {
            LOGGER.warn("User does not have permission to save assets");
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to edit this asset");
        }
    }

    /**
     * Empties the draft data so fetchDraftAssets() will return the Live data again.
     * @param ownerDocId The document that owns these assets.
     * @throws IOException If something goes wrong.
     */
    public void revertDraftFromLive(final String ownerDocId) throws IOException {
        final DocRef docRef = new DocRef(VisualisationDoc.TYPE, ownerDocId);
        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)) {
            dao.revertDraftFromLive(securityContext.getUserRef().getUuid(), ownerDocId);
        } else {
            LOGGER.warn("User does not have permission to revert changes");
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to edit this asset");
        }
    }

    /**
     * Performs the SaveAs operation, when invoked from the UI.
     * @param fromOwnerDocId The document ID that is being saved
     * @param toOwnerDocId Where the from document is being saved to
     * @param updatedContentPath Path of any updated content that needs to be saved.
     *                           Can be null if no such content.
     * @param updatedContent Any updated content that needs to be saved. Can be null if no such content.
     * @throws IOException If something goes wrong.
     */
    public void saveAs(final String fromOwnerDocId,
                       final String toOwnerDocId,
                       final String updatedContentPath,
                       final byte[] updatedContent) throws IOException {
        final DocRef fromDocRef = new DocRef(VisualisationDoc.TYPE, fromOwnerDocId);
        final DocRef toDocRef = new DocRef(VisualisationDoc.TYPE, toOwnerDocId);
        if (securityContext.hasDocumentPermission(fromDocRef, DocumentPermission.VIEW)) {
            if (securityContext.hasDocumentPermission(toDocRef, DocumentPermission.EDIT)) {
                dao.saveAs(securityContext.getUserRef().getUuid(),
                        fromOwnerDocId,
                        toOwnerDocId,
                        updatedContentPath,
                        updatedContent);
            } else {
                LOGGER.warn("User does not have permission to saveAs this document");
                throw new PermissionException(securityContext.getUserRef(),
                        "You do not have permission to view this asset");
            }
        } else {
            LOGGER.warn("User does not have permission to save this document to a new document");
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to create this asset");
        }
    }

    /**
     * Gets the data for a given asset. Called from the Servlet to get the asset for a given
     * document and path.
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
    Instant writeLiveToServletCache(final String tempFilePrefix,
                                    final String tempFileSuffix,
                                    final String ownerDocId,
                                    final String assetPath,
                                    final Instant cacheTimestamp,
                                    final Path cachedPath)
            throws IOException, PermissionException {

        final DocRef docRef = new DocRef(VisualisationDoc.TYPE, ownerDocId);
        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
            return dao.writeLiveToServletCache(tempFilePrefix,
                    tempFileSuffix,
                    ownerDocId,
                    assetPath,
                    cacheTimestamp,
                    cachedPath);
        } else {
            // Catch this higher up and return a 401.
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to view this asset");
        }
    }

    /**
     * Returns the assets in a form suitable for exporting.
     * @param docRef The ref of the owning document
     * @return Assets to export. Never null.
     * @throws IOException If something goes wrong
     * @throws PermissionException If the user doesn't have permission
     */
    Collection<ImportExportAsset> getAssetsForExport(final DocRef docRef)
            throws IOException, PermissionException {
        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
            return dao.getAssetsForExport(docRef.getUuid());
        } else {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to view this asset");
        }
    }

    /**
     * Sets assets for this visualisation during import.
     * @param docRef The document that owns these assets.
     * @param pathAssets The assets associated with the doc.
     * @throws IOException If something goes wrong.
     * @throws PermissionException If the user doesn't have EDIT permission.
     */
    void setAssetsFromImport(final DocRef docRef,
                             final Collection<ImportExportAsset> pathAssets)
        throws IOException, PermissionException {

        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)) {
            dao.setAssetsFromImport(docRef.getUuid(), pathAssets);
        } else {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to import these assets");
        }
    }

    /**
     * Copies assets from the source document into the destination document.
     * Any assets already in the destination document won't be deleted.
     * @param fromDocRef Where the assets are coming from.
     * @param toDocRef Where the assets are going.
     * @throws IOException If something goes wrong
     * @throws PermissionException If the user doesn't have EDIT permission
     */
    void copyAssetsToDoc(final DocRef fromDocRef,
                         final DocRef toDocRef)
        throws IOException, PermissionException {

        LOGGER.info("Copying assets from {} to {}", fromDocRef, toDocRef);

        if (securityContext.hasDocumentPermission(fromDocRef, DocumentPermission.EDIT)) {
            if (securityContext.hasDocumentPermission(toDocRef, DocumentPermission.EDIT)) {
                dao.copyLiveAssets(fromDocRef.getUuid(), toDocRef.getUuid());
            } else {
                throw new PermissionException(securityContext.getUserRef(),
                        "You do not have permission to copy to the destination document");
            }
        } else {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to copy the source document");
        }
    }

    /**
     * Deletes the assets associated with a document.
     * Called when the document is deleted.
     * @param docRef The document that has been deleted.
     * @throws IOException If something goes wrong
     * @throws PermissionException If the user does not have DELETE permission.
     */
    void deleteAssetsForDoc(final DocRef docRef)
        throws IOException, PermissionException {

        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.DELETE)) {
            dao.deleteAssetsForDoc(docRef.getUuid());
        } else {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to delete these assets");
        }
    }

}
