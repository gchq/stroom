/*
 * Copyright 2016 Crown Copyright
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

package stroom.document.client.component;

import stroom.explorer.client.component.DocumentPicker;
import stroom.explorer.shared.PermissionInheritance;
import stroom.widget.component.client.Component;
import stroom.widget.selection.client.SelectionBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// Where to move documents to: the folder, and how their permissions are set.
public class MoveDocument extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    DocumentPicker folders;
    @UiField
    SelectionBox<PermissionInheritance> permissionInheritance;

    /// Makes an empty form, offering every permission inheritance, Destination chosen.
    public MoveDocument() {
        initWidget(BINDER.createAndBindUi(this));
        permissionInheritance.addItem(PermissionInheritance.NONE);
        permissionInheritance.addItem(PermissionInheritance.SOURCE);
        permissionInheritance.addItem(PermissionInheritance.DESTINATION);
        permissionInheritance.addItem(PermissionInheritance.COMBINED);
        permissionInheritance.setValue(PermissionInheritance.DESTINATION);
    }

    /// @return The folder picker, for its controller to attach to.
    public DocumentPicker getFolders() {
        return folders;
    }

    /// @return How the documents' permissions are set.
    public PermissionInheritance getPermissionInheritance() {
        return permissionInheritance.getValue();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, MoveDocument> {

    }
}
