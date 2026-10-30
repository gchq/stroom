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

package stroom.document.asset.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;
import stroom.widget.text.client.Label;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The content of the dialog that adds a new, empty file or folder to a document's assets: the
/// folder it goes in, and its name. Uploading a file is the upload dialog's.
public class DocumentAssetAddItem extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox txtName;
    @UiField
    Label lblPath;

    /// Makes empty content.
    public DocumentAssetAddItem() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Puts the focus in the name.
    public void focus() {
        txtName.setFocus(true);
    }

    /// @param path The folder the new file or folder goes in, shown so the user knows where it goes.
    public void setPath(final String path) {
        lblPath.setText(path);
    }

    /// @return The name entered for the new file or folder.
    public String getName() {
        return txtName.getText();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DocumentAssetAddItem> {

    }
}
