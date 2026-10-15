/*
 * Copyright 2026 Crown Copyright
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

package stroom.document.asset.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestFactory;
import stroom.docstore.shared.AbstractDoc;
import stroom.document.asset.client.presenter.assets.DocumentAssetTreeItem;
import stroom.document.asset.shared.DocumentAsset;
import stroom.document.asset.shared.DocumentAssetResource;
import stroom.document.asset.shared.DocumentAssetUpdateNewFile;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.ResourceKey;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Uploads a single file straight into the <em>root</em> of a document's asset
 * store and hands back the URL to reference it by, without the caller needing the
 * Assets tab's tree UI.
 *
 * <p>This exists so a dialog that merely <em>references</em> an asset (for
 * example the Floor Map layer appearance dialog, which points a layer at an
 * image) can offer "upload a new one" inline. It reuses the standard
 * {@link DocumentAssetUploadFileDialogPresenter} for the file chooser and the
 * standard {@code updateNewUploadedFile} endpoint for the upload, so a file
 * uploaded here is indistinguishable from one added on the Assets tab.</p>
 *
 * <p><strong>The upload lands in the draft asset store.</strong> Like every other
 * asset edit it only becomes servable once the owning document is saved, so a
 * freshly uploaded image is pickable immediately but will not render until save.
 * Callers that display the image should say so.</p>
 */
public class DocumentAssetQuickUploadPresenter implements DocumentAssetAddFileCallback, HasHandlers {

    private static final DocumentAssetResource DOCUMENT_ASSET_RESOURCE =
            GWT.create(DocumentAssetResource.class);

    /** Characters that cannot appear in an asset name (mirrors the Assets tab). */
    private static final String ILLEGAL_ASSET_NAME_CHARACTERS = "/:";

    private final EventBus eventBus;
    private final RestFactory restFactory;
    private final Provider<DocumentAssetUploadFileDialogPresenter> uploadDialogProvider;

    /** Root-level asset names already in use, so an upload never silently replaces one. */
    private final Set<String> existingRootNames = new HashSet<>();

    private AbstractDoc document;
    private TaskMonitorFactory taskMonitorFactory;
    private Consumer<String> onUploaded;

    @Inject
    public DocumentAssetQuickUploadPresenter(
            final EventBus eventBus,
            final RestFactory restFactory,
            final Provider<DocumentAssetUploadFileDialogPresenter> uploadDialogProvider) {
        this.eventBus = eventBus;
        this.restFactory = restFactory;
        this.uploadDialogProvider = uploadDialogProvider;
    }

    /**
     * Shows the Add File dialog and uploads the chosen file to the root of
     * {@code document}'s asset store.
     *
     * <p>The document's existing root-level names are fetched first so a clashing
     * filename can be given a numbered suffix rather than overwriting.</p>
     *
     * @param document           the asset store's owning document
     * @param taskMonitorFactory task monitor for the fetch and upload calls
     * @param onUploaded         called with the {@code /assets/<uuid>/<name>} URL of
     *                           the uploaded file; not called if the user cancels
     */
    public void upload(final AbstractDoc document,
                       final TaskMonitorFactory taskMonitorFactory,
                       final Consumer<String> onUploaded) {
        if (document == null) {
            return;
        }
        this.document = document;
        this.taskMonitorFactory = taskMonitorFactory;
        this.onUploaded = onUploaded;

        restFactory.create(DOCUMENT_ASSET_RESOURCE)
                .method(r -> r.fetchDraftAssets(document.getUuid()))
                .onSuccess(assets -> {
                    existingRootNames.clear();
                    if (assets != null && assets.getAssets() != null) {
                        for (final DocumentAsset asset : assets.getAssets()) {
                            final String name = rootName(asset.getPath());
                            if (name != null) {
                                existingRootNames.add(name);
                            }
                        }
                    }
                    showUploadDialog();
                })
                .onFailure(error -> AlertEvent.fireError(this,
                        "Unable to read the document's assets: " + error.getMessage(),
                        null))
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    private void showUploadDialog() {
        uploadDialogProvider.get().fireShowPopup(
                this,
                // Always the root of the asset store — no tree to pick a folder from.
                null,
                "/",
                ILLEGAL_ASSET_NAME_CHARACTERS);
    }

    /**
     * The first path segment of an asset path, i.e. its name when the asset sits
     * at the root. A nested asset's top-level folder name is returned, which is
     * exactly what a root-level upload would clash with.
     */
    private static String rootName(final String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        final String trimmed = path.startsWith("/")
                ? path.substring(1)
                : path;
        final int slash = trimmed.indexOf('/');
        final String name = slash == -1
                ? trimmed
                : trimmed.substring(0, slash);
        return name.isEmpty()
                ? null
                : name;
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code parentItem} is always {@code null} here — this uploader only ever
     * writes to the root — so the check is against the root names fetched in
     * {@link #upload}. A clashing {@code name.png} becomes {@code name_2.png}.</p>
     */
    @Override
    public String getNonClashingLabel(final DocumentAssetTreeItem parentItem,
                                      final String itemLabel,
                                      final String itemId) {
        if (itemLabel == null || !existingRootNames.contains(itemLabel)) {
            return itemLabel;
        }
        final int dot = itemLabel.lastIndexOf('.');
        final String stem = dot > 0
                ? itemLabel.substring(0, dot)
                : itemLabel;
        final String extension = dot > 0
                ? itemLabel.substring(dot)
                : "";
        int suffix = 2;
        String candidate = stem + "_" + suffix + extension;
        while (existingRootNames.contains(candidate)) {
            suffix++;
            candidate = stem + "_" + suffix + extension;
        }
        return candidate;
    }

    @Override
    public void addUploadedFile(final DocumentAssetTreeItem parentFolderItem,
                                final String fileName,
                                final ResourceKey resourceKey) {
        if (document == null || fileName == null || resourceKey == null) {
            return;
        }
        final String docUuid = document.getUuid();
        // A root-level new-item path is the bare filename (see
        // DocumentAssetPresenterUtils.getNewItemPath), whereas the servlet URL
        // always has a separator after the document UUID.
        restFactory.create(DOCUMENT_ASSET_RESOURCE)
                .method(r -> r.updateNewUploadedFile(docUuid,
                        new DocumentAssetUpdateNewFile(fileName, resourceKey)))
                .onSuccess(ok -> {
                    if (Boolean.TRUE.equals(ok)) {
                        if (onUploaded != null) {
                            onUploaded.accept(DocumentAssetPresenter.ASSET_SERVLET_PATH_PREFIX
                                    + docUuid + "/" + fileName);
                        }
                    } else {
                        AlertEvent.fireError(this, "There was an error uploading the file", null);
                    }
                })
                .onFailure(error -> AlertEvent.fireError(this,
                        "There was an error uploading the file: " + error.getMessage(),
                        null))
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
