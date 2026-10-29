/*
 * Copyright 2022 Crown Copyright
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

package stroom.security.client.component;

import stroom.item.client.SelectionBox;
import stroom.security.shared.PermissionShowLevel;
import stroom.util.shared.UserRef;
import stroom.util.shared.string.CaseType;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.text.client.Html;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// A document's permissions: which users and groups to list, the list, and the details of the
/// permissions of the one chosen.
public class DocumentUserPermissionsTab extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String DOC_PERM_DETAILS_BASE_LABEL = "Document Permission Details";

    @UiField
    SelectionBox<PermissionShowLevel> permissionVisibility;
    @UiField
    DocumentUserPermissionsList docUserPermissionsList;
    @UiField
    FormGroup detailsFormGroup;
    @UiField
    Html details;

    private Consumer<PermissionShowLevel> onPermissionVisibilityChange = level -> {
    };

    /// Makes the tab, listing those with explicit permissions.
    public DocumentUserPermissionsTab() {
        initWidget(BINDER.createAndBindUi(this));
        permissionVisibility.addValueChangeHandler(this::onPermissionVisibility);
        permissionVisibility.addItems(PermissionShowLevel.ITEMS);
        permissionVisibility.setValue(PermissionShowLevel.SHOW_EXPLICIT);
    }

    /// @return The users and groups.
    public DocumentUserPermissionsList getList() {
        return docUserPermissionsList;
    }

    /// @return Which users and groups are listed.
    public PermissionShowLevel getPermissionVisibility() {
        return permissionVisibility.getValue();
    }

    /// @param details The chosen user or group's permissions, described.
    public void setDetails(final SafeHtml details) {
        this.details.setHTML(details);
    }

    /// @param userRef The chosen user or group, named in the details' label, or `null` for none.
    public void setUserRef(final UserRef userRef) {
        if (userRef == null) {
            detailsFormGroup.setLabel(DOC_PERM_DETAILS_BASE_LABEL + ":");
        } else {
            final String suffix = " for " + userRef.getType(CaseType.LOWER)
                                  + " \"" + userRef.getDisplayName() + "\":";
            detailsFormGroup.setLabel(DOC_PERM_DETAILS_BASE_LABEL + suffix);
        }
    }

    /// @param onPermissionVisibilityChange Given which users and groups to list, when the user
    ///                                     changes it.
    public void setOnPermissionVisibilityChange(final Consumer<PermissionShowLevel> onPermissionVisibilityChange) {
        this.onPermissionVisibilityChange = onPermissionVisibilityChange;
    }

    private void onPermissionVisibility(final ValueChangeEvent<PermissionShowLevel> event) {
        onPermissionVisibilityChange.accept(permissionVisibility.getValue());
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DocumentUserPermissionsTab> {

    }
}
