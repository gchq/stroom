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

import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The content of the dialog that renames a file or folder in a document's assets: its name.
public class DocumentAssetRename extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox txtAssetName;

    /// Makes empty content.
    public DocumentAssetRename() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Puts the focus in the name.
    public void focus() {
        txtAssetName.setFocus(true);
    }

    /// @return The name entered.
    public String getName() {
        return txtAssetName.getText();
    }

    /// @param name The name to start with.
    public void setName(final String name) {
        txtAssetName.setText(name);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DocumentAssetRename> {

    }
}
