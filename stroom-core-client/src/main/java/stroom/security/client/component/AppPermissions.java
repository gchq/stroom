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
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;
import java.util.function.Consumer;

/// The Application Permissions screen: users and groups, and the selected one's permissions.
public class AppPermissions extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SelectionBox<PermissionShowLevel> permissionVisibility;
    @UiField
    AppUserPermissionsList appUserPermissionsList;
    @UiField
    AppPermissionsEditor appPermissionsEdit;

    private Consumer<PermissionShowLevel> onShowLevelChange = showLevel -> {
    };

    /// Makes the screen, showing explicit permissions, with its list and permissions empty.
    public AppPermissions() {
        initWidget(BINDER.createAndBindUi(this));
        permissionVisibility.addItems(PermissionShowLevel.ITEMS);
        permissionVisibility.setValue(PermissionShowLevel.SHOW_EXPLICIT);
        permissionVisibility.addValueChangeHandler(event -> onShowLevelChange.accept(event.getValue()));
    }

    /// @return Which users and groups the user has chosen to see: those with explicit permissions,
    /// effective ones, or all.
    public PermissionShowLevel getShowLevel() {
        return permissionVisibility.getValue();
    }

    /// @param onShowLevelChange Given the users and groups to see when the user chooses them.
    public void setOnShowLevelChange(final Consumer<PermissionShowLevel> onShowLevelChange) {
        this.onShowLevelChange = Objects.requireNonNull(onShowLevelChange, "onShowLevelChange");
    }

    /// @return The list of users and groups.
    public AppUserPermissionsList getAppUserPermissionsList() {
        return appUserPermissionsList;
    }

    /// @return The selected user's or group's permissions.
    public AppPermissionsEditor getAppPermissionsEdit() {
        return appPermissionsEdit;
    }

    // --------------------------------------------------------------------------------

    interface Binder extends UiBinder<Widget, AppPermissions> {

    }
}
