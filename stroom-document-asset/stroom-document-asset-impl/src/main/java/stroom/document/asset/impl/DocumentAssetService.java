/*
 * Copyright 2025 Crown Copyright
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

package stroom.document.asset.impl;

import stroom.docref.DocRef;
import stroom.document.asset.shared.DocumentAssets;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerNode;
import stroom.importexport.api.ImportExportAsset;
import stroom.resource.api.ResourceStore;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.io.ByteSize;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResourceKey;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;

/**
 * Intermediates between DocumentAssetResource and DocumentAssetDao.
 * Primarily responsible for checking permissions.
 * Allows easy access to Assets within the database.
 */
public class DocumentAssetService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocumentAssetService.class);

    /** DAO to talk to the DB */
    private final DocumentAssetDao dao;

    /** Allows access to uploaded files */
    private final ResourceStore resourceStore;

    /** Security checks */
    private final SecurityContext securityContext;

    private final Provider<ExplorerNodeService> explorerNodeServiceProvider;

    /**
     * Read per upload rather than cached, so raising the limit takes effect without a restart —
     * which matters because the symptom of it being too low is a rejected upload.
     */
    private final Provider<DocumentAssetConfig> configProvider;

    @SuppressWarnings("unused")
    @Inject
    public DocumentAssetService(final DocumentAssetDao dao,
                                final ResourceStore resourceStore,
                                final SecurityContext securityContext,
                                final Provider<ExplorerNodeService> explorerNodeServiceProvider,
                                final Provider<DocumentAssetConfig> configProvider) {
        this.dao = dao;
        this.resourceStore = resourceStore;
        this.securityContext = securityContext;
        this.explorerNodeServiceProvider = explorerNodeServiceProvider;
        this.configProvider = configProvider;
    }

    private DocRef getDocRef(final String ownerId) {
        return explorerNodeServiceProvider.get().getNodeByUuid(ownerId)
                .map(ExplorerNode::getDocRef)
                .orElseThrow(() -> new EntityServiceException("Unknown document " + ownerId));
    }

    /**
     * Used by the UI to get all the asset metadata associated with a document.
     * @param ownerId The ID of the document that owns these assets.
     * @return An object that holds all the metadata about the assets. Note that
     *         getUploadedFiles() will always return an empty map.
     *         This will be the draft assets for the user logged in.
     * @throws IOException if something goes wrong.
     */
    DocumentAssets fetchDraftAssets(final String ownerId) throws IOException {
        final DocRef docRef = getDocRef(ownerId);
        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
            return dao.fetchDraftAssets(securityContext.getUserRef().getUuid(), ownerId);
        } else {
            // No permission so return empty assets
            LOGGER.warn("User does not have permission to see assets");
            return new DocumentAssets(ownerId);
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

        final DocRef docRef = getDocRef(ownerDocId);
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

        final DocRef docRef = getDocRef(ownerDocId);
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

        final DocRef docRef = getDocRef(ownerDocId);
        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)) {
            final Path uploadPath = resourceStore.getTempFile(resourceKey);
            if (!uploadPath.toFile().exists()) {
                throw new IOException("The uploaded file does not exist");
            }

            // Check the size before anything reaches the database. Nothing bounded this, so the
            // only limits were the client's patience and the column's; a file too large to be
            // useful still became a row that every export then had to carry.
            //
            // The upload is already a temp file by this point, so this is one stat call rather
            // than a counting stream. It does mean the bytes have been accepted and written to
            // disk before being refused - bounding that would have to happen in the HTTP layer,
            // which is a different concern - but the durable cost is the blob, and this stops it.
            final ByteSize maxUploadSize = configProvider.get().getMaxUploadSize();
            final long uploadSize = Files.size(uploadPath);
            if (maxUploadSize != null && uploadSize > maxUploadSize.getBytes()) {
                resourceStore.deleteTempFile(resourceKey);
                throw new IOException("The uploaded file is "
                                      + ModelStringUtil.formatIECByteSizeString(uploadSize)
                                      + ", which is larger than the maximum of "
                                      + maxUploadSize
                                      + " (documentAsset.maxUploadSize). Upload a smaller file, or "
                                      + "raise that limit.");
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

        final DocRef docRef = getDocRef(ownerDocId);
        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)) {
            LOGGER.info("User has permission to delete an asset {}", docRef);
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

        final DocRef docRef = getDocRef(ownerDocId);
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

        final DocRef docRef = getDocRef(ownerDocId);
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
     *
     * <p>Not every unviewable asset returns null: content that is simply too large to edit
     * <strong>throws</strong> rather than returning null, so a caller that only null-checks will
     * see an uncaught exception on a large asset.</p>
     *
     * @param ownerDocId Document that owns the assets. Must not be null.
     * @param path Path of the asset to read the content of. Must not be null.
     * @return The content, or null if the asset is missing or its bytes are not valid UTF-8
     *         text.
     * @throws RuntimeException {@code DataTooBigException} from the DAO if the content exceeds
     *         the maximum editable size. Named rather than linked because it lives in the
     *         impl-db module, which this one does not depend on.
     */
    String getDraftContent(final String ownerDocId,
                           final String path)
            throws IOException {

        Objects.requireNonNull(ownerDocId);
        Objects.requireNonNull(path);

        final DocRef docRef = getDocRef(ownerDocId);
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

        final DocRef docRef = getDocRef(ownerDocId);
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
        final DocRef docRef = getDocRef(ownerDocId);
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
        final DocRef fromDocRef = getDocRef(fromOwnerDocId);
        final DocRef toDocRef = getDocRef(toOwnerDocId);
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
     *                   DocumentAssetServlet cache. This method will write
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

        final DocRef docRef = getDocRef(ownerDocId);
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
    public Collection<ImportExportAsset> getAssetsForExport(final DocRef docRef)
            throws IOException, PermissionException {
        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
            return dao.getAssetsForExport(docRef.getUuid());
        } else {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to view this asset");
        }
    }

    /**
     * Sets assets for this document during import.
     * @param docRef The document that owns these assets.
     * @param pathAssets The assets associated with the doc.
     * @throws IOException If something goes wrong.
     * @throws PermissionException If the user doesn't have EDIT permission.
     */
    public void setAssetsFromImport(final DocRef docRef,
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
    public void copyAssetsToDoc(final DocRef fromDocRef,
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
    public void deleteAssetsForDoc(final DocRef docRef)
        throws IOException, PermissionException {

        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.DELETE)) {
            dao.deleteAssetsForDoc(docRef.getUuid());
        } else {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to delete these assets");
        }
    }

    /**
     * Called to check if an asset named index.html exists.
     * @param ownerDocId The document that owns the assets
     * @return true if index.html exists; false if not
     * @throws IOException If something goes wrong
     * @throws PermissionException If the user does not have VIEW permission
     */
    boolean indexAssetExists(final String ownerDocId) throws IOException, PermissionException {
        final DocRef docRef = getDocRef(ownerDocId);
        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
            return dao.indexAssetExists(ownerDocId);
        } else {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to view these assets");
        }
    }

}
