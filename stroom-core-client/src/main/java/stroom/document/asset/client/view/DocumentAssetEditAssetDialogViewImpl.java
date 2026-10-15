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

package stroom.document.asset.client.view;

import stroom.document.asset.client.presenter.DocumentAssetEditAssetDialogPresenter.DocumentAssetEditAssetDialogView;
import stroom.document.asset.client.presenter.assets.DocumentAssetTreeItem;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

/**
 * View for the dialog that renames an existing asset in the document asset manager.
 */
public class DocumentAssetEditAssetDialogViewImpl extends ViewImpl implements
        DocumentAssetEditAssetDialogView {

    /** GWT widget */
    private final Widget widget;

    /** Whether this is a folder or file - useful in error messages */
    private boolean leaf;

    /** ID of the item */
    private String id;

    @UiField
    TextBox txtAssetName;

    /**
     * Injected constructor.
     */
    @Inject
    @SuppressWarnings("unused")
    public DocumentAssetEditAssetDialogViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    /**
     * Sets the tree item we're editing.
     */
    @Override
    public void setTreeItem(final DocumentAssetTreeItem assetTreeItem) {
        this.txtAssetName.setText(assetTreeItem.getText());
        this.id = assetTreeItem.getId();
        this.leaf = assetTreeItem.isLeaf();
    }

    /**
     * Returns the name currently entered in the dialog, which may have been edited.
     */
    @Override
    public String getText() {
        return txtAssetName.getText();
    }

    /**
     * Returns whether this is a file (true) or a folder (false)
     */
    @Override
    public boolean isLeaf() {
        return leaf;
    }

    /**
     * @return The ID of the asset being edited.
     */
    @Override
    public String getId() {
        return id;
    }

    /**
     * Interface to keep GWT UiBinder happy.
     */
    public interface Binder extends UiBinder<Widget, DocumentAssetEditAssetDialogViewImpl> {
        // No code
    }
}
