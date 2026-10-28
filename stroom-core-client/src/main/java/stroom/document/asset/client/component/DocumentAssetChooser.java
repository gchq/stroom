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

package stroom.document.asset.client.component;

import stroom.document.asset.client.DocumentAssetUtils;
import stroom.document.asset.shared.DocumentAsset;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/// The "Choose Asset" dialog's content: a document's assets as a tree, in which a file is chosen.
/// Its [DocumentAssetChooserController] fetches the assets and shows it.
public class DocumentAssetChooser extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    DocumentAssetTree tree;

    // Which folders were open, kept when the assets are shown again
    private final Set<String> openFolders = new HashSet<>();

    /// Makes an empty chooser.
    public DocumentAssetChooser() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Shows a document's assets, keeping the folders that were open open.
    ///
    /// @param assets The assets.
    public void setAssets(final List<DocumentAsset> assets) {
        DocumentAssetUtils.storeOpenClosedState(tree, openFolders);
        tree.clear();
        DocumentAssetUtils.addPathsToTree(tree, assets);
        DocumentAssetUtils.restoreOpenClosedState(tree, openFolders);
    }

    /// Sets what is told when the choice changes (it follows the focus).
    ///
    /// @param onSelect Given the item chosen, a file or a folder, or null for none.
    public void setOnSelect(final Consumer<DocumentAssetTreeItem> onSelect) {
        tree.setOnSelect(onSelect);
    }

    /// Sets what Enter on a file, or a double click on one, does, e.g. choosing it as OK does.
    ///
    /// @param onOpen Given the file.
    public void setOnOpen(final Consumer<DocumentAssetTreeItem> onOpen) {
        tree.setOpenHandler(onOpen);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DocumentAssetChooser> {

    }
}
