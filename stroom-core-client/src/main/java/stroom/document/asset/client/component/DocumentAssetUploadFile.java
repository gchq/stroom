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
import stroom.widget.form.client.component.FileChooser;
import stroom.widget.text.client.Label;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The content of the dialog that uploads a file into a document's assets: the folder it goes in,
/// and the file.
public class DocumentAssetUploadFile extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FileChooser fileChooser;
    @UiField
    Label lblPath;

    /// Makes empty content.
    public DocumentAssetUploadFile() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @param path The folder the file goes in, shown so the user knows where it goes.
    public void setPath(final String path) {
        lblPath.setText(path);
    }

    /// @return The file chooser, for its controller to attach to.
    public FileChooser getFileChooser() {
        return fileChooser;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DocumentAssetUploadFile> {

    }
}
