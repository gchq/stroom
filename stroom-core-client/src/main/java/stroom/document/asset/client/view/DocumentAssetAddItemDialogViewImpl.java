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

package stroom.document.asset.client.view;

import stroom.document.asset.client.presenter.DocumentAssetAddItemDialogPresenter.DocumentAssetAddItemDialogView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

/**
 * View for the dialog that adds a new, empty file or folder to the document asset manager.
 * Performs no upload - that is DocumentAssetUploadFileDialogViewImpl.
 */
public class DocumentAssetAddItemDialogViewImpl extends ViewImpl implements
        DocumentAssetAddItemDialogView {

    /** GWT widget */
    private final Widget widget;

    @UiField
    TextBox txtName;

    @UiField
    Label lblPath;

    /**
     * Injected constructor.
     */
    @Inject
    @SuppressWarnings("unused")
    public DocumentAssetAddItemDialogViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    /**
     * Sets the path where this file will be placed. Provided as an aid for the
     * user so they know where stuff will go.
     * @param path The path to display to the user.
     */
    @Override
    public void setPath(final String path) {
        lblPath.setText(path);
    }

    /**
     * Gets the name entered for the folder.
     */
    @Override
    public String getName() {
        return txtName.getText();
    }

    /**
     * Interface to keep GWT UiBinder happy.
     */
    public interface Binder extends UiBinder<Widget, DocumentAssetAddItemDialogViewImpl> {
        // No code
    }
}
