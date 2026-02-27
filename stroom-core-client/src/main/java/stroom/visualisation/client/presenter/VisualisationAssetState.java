/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.visualisation.client.presenter;

import stroom.visualisation.client.presenter.assets.VisualisationAssetTreeItem;

import java.util.Objects;

/**
 * Holds the state of the asset content
 * and allows it to change based on the editor,
 * loading, updating etc.
 */
public class VisualisationAssetState {

    /** The tree item currently being edited */
    private VisualisationAssetTreeItem editItem = null;

    /** The path to the tree item currently being edited */
    private String pathToEditItem = null;

    /** Is the content in the editor dirty? */
    private boolean dirtyAssetContent = false;

    /**
     * Called on successful return of updateContent
     */
    public void onUpdateContentSuccess() {
        dirtyAssetContent = false;
    }

    /**
     * Called when a new item is selected after the old one has
     * been saved.
     * @param selectedItem The item that is selected. Can be null if nothing is selected.
     * @param selectedItemPath The path to the selected item. Can be null if selectedItem is also null.
     */
    public void onSelectNewItemAfterSaveOldItem(final VisualisationAssetTreeItem selectedItem,
                                                final String selectedItemPath) {
        if (selectedItem != null) {
            Objects.requireNonNull(selectedItemPath);
        }
        editItem = selectedItem;
        pathToEditItem = selectedItemPath;
        dirtyAssetContent = false;
    }

    /**
     * Called on successful return of fetchDraftAssets.
     */
    public void onFetchDraftAssets() {
        editItem = null;
        pathToEditItem = null;
        dirtyAssetContent = false;
    }

    /**
     * Called when asset content changes.
     */
    public void onAssetContentChanged() {
        dirtyAssetContent = true;
    }

    /**
     * Returns whether the item is dirty and needs saving to draft.
     */
    public boolean isDirtyAndNeedsSaveToDraft() {
        return pathToEditItem != null && dirtyAssetContent;
    }

    /**
     * Returns the tree item that is currently being edited.
     * Can return null, but never if isDirtyAndNeedsSaveToDraft() returns true.
     */
    public VisualisationAssetTreeItem getEditItem() {
        return editItem;
    }

    /**
     * Returns the path to the tree item that is currently
     * being edited. Can return null.
     */
    public String getPathToEditItem() {
        return pathToEditItem;
    }

    @Override
    public String toString() {
        return "VisualisationAssetState{" +
               "dirtyAssetContent=" + dirtyAssetContent +
               ", pathToEditItem='" + pathToEditItem + '\'' +
               '}';
    }
}
