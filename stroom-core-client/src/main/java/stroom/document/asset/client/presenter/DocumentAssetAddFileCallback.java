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

package stroom.document.asset.client.presenter;

import stroom.document.asset.client.presenter.assets.DocumentAssetTreeItem;
import stroom.util.shared.ResourceKey;

/**
 * Callback interface, so that the Add File dialog can record what it has done back
 * into the DocumentAssetAddDialogPresenter.
 */
public interface DocumentAssetAddFileCallback {

    /**
     * Method for the Add File dialog to call when it has uploaded a file.
     * @param parentFolderItem The node that the file has been added to.
     * @param fileName The name of the file that was uploaded.
     * @param resourceKey The resource key of the file, so that the server can find it later.
     */
    void addUploadedFile(DocumentAssetTreeItem parentFolderItem,
                         String fileName,
                         ResourceKey resourceKey);

    /**
     * Generates a label that doesn't clash with other files/folders in the same directory.
     * Adds an integer to the end, incrementing until an integer is found that doesn't
     * clash with anything else.
     * @param parentItem The node that holds the directory. Can be null
     * @param itemLabel The label that we're trying to put into the directory.
     * @param itemId The ID of the item owning the label, so we don't signal a clash with ourselves.
     *               Can be null if this is a new item with no ID yet.
     * @return A label that doesn't clash with anything else.
     */
    String getNonClashingLabel(DocumentAssetTreeItem parentItem, String itemLabel, String itemId);

}

