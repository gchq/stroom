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

package stroom.security.client.component;

import stroom.cell.tickbox.shared.TickBoxState;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AbstractAppPermissionChange;
import stroom.security.shared.AbstractAppPermissionChange.AddAppPermission;
import stroom.security.shared.AbstractAppPermissionChange.RemoveAppPermission;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppPermissionResource;
import stroom.security.shared.AppUserPermissionsReport;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/// A user's or group's application permissions ([AppPermissionsEdit]): fetched for the user given
/// with [#setUserRef(UserRef)], granted or revoked as their tick boxes are pressed (by those who
/// may manage users), with how the one selected is held.
public class AppPermissionsEditController extends Controller {

    private static final AppPermissionResource APP_PERMISSION_RESOURCE = GWT.create(AppPermissionResource.class);

    private final RestFactory restFactory;
    private final ClientSecurityContext securityContext;

    private AppPermissionsEdit view;
    private AppUserPermissionsReport currentPermissions;
    private List<AppPermission> allPermissions;

    private UserRef relatedUser;
    private Consumer<ValueChange> onChange = change -> {
    };

    /// Made by GIN; [#attach(AppPermissionsEdit)] gives it its content.
    ///
    /// @param eventBus        The application's event bus.
    /// @param restFactory     Fetches and changes the permissions.
    /// @param securityContext Says whether the user may manage users, so change permissions.
    @Inject
    public AppPermissionsEditController(final EventBus eventBus,
                                        final RestFactory restFactory,
                                        final ClientSecurityContext securityContext) {
        super(eventBus);
        this.restFactory = restFactory;
        this.securityContext = securityContext;
    }

    /// Attaches this to its content, e.g. one a template declares. The content is then this
    /// controller's widget, e.g. to show it as a tab.
    ///
    /// @param view The content.
    /// @return This, for its owner to keep.
    public AppPermissionsEditController attach(final AppPermissionsEdit view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);

        view.setPermissionState(permission -> tickBoxState(currentPermissions, permission));
        view.setOnTick(this::onTick);
        view.addColumns(securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION));
        view.setUserRef(null);
        view.setOnSelectionChange(this::updateDetails);
        view.setOnRefresh(this::refresh);
        return this;
    }

    /// Shows a user's or group's permissions.
    ///
    /// @param userRef The user or group, or `null` for none.
    public void setUserRef(final UserRef userRef) {
        this.relatedUser = userRef;
        view.setUserRef(userRef);
        refresh();
    }

    /// Fetches the permissions again.
    public void refresh() {
        if (relatedUser == null) {
            currentPermissions = null;
            // Update details panel.
            onChange.accept(new ValueChange(false, true));
            view.setRows(new ArrayList<>());
            updateDetails();
        } else {
            // Fetch permissions and populate table.
            restFactory
                    .create(APP_PERMISSION_RESOURCE)
                    .method(res -> res.getAppUserPermissionsReport(relatedUser))
                    .onSuccess(userAppPermissions -> {
                        currentPermissions = userAppPermissions;
                        updateAllPermissions();
                        updateDetails();
                    })
                    .owner(this)
                    .taskMonitorFactory(view.getTaskMonitor())
                    .exec();
        }
    }

    /// Sets what to run when a change means its owner should refresh the users or the details.
    ///
    /// @param onChange What to run with what to refresh.
    public void setOnChange(final Consumer<ValueChange> onChange) {
        this.onChange = Objects.requireNonNull(onChange, "onChange");
    }

    /// Shows again how the permission selected is held.
    public void updateDetails() {
        view.setDetails(getDetails());
    }

    /// The state of a permission's tick box: ticked if held explicitly, half ticked if inherited or
    /// implied by Administrator (held or inherited), and unticked otherwise.
    ///
    /// @param report     The user's permissions, or `null` before they are fetched.
    /// @param permission The permission.
    /// @return The tick box state.
    static TickBoxState tickBoxState(final AppUserPermissionsReport report, final AppPermission permission) {
        if (report != null) {
            if (NullSafe.collectionContains(report.getExplicitPermissions(), permission)) {
                return TickBoxState.TICK;
            } else if (report.getInheritedPermissions().containsKey(permission)) {
                return TickBoxState.HALF_TICK;
            }

            // See if implied by administrator.
            if (!AppPermission.ADMINISTRATOR.equals(permission)) {
                if (NullSafe.collectionContains(report.getExplicitPermissions(), AppPermission.ADMINISTRATOR)) {
                    return TickBoxState.HALF_TICK;
                } else if (NullSafe.containsKey(report.getInheritedPermissions(), AppPermission.ADMINISTRATOR)) {
                    return TickBoxState.HALF_TICK;
                }
            }
        }
        return TickBoxState.UNTICK;
    }

    private void onTick(final AppPermission permission, final boolean ticked) {
        final AbstractAppPermissionChange request;
        if (ticked) {
            request = new AddAppPermission(relatedUser, permission);
        } else {
            request = new RemoveAppPermission(relatedUser, permission);
        }

        restFactory
                .create(APP_PERMISSION_RESOURCE)
                .method(res -> res.changeAppPermission(request))
                .onSuccess(result -> {
                    refresh();
                    // Refresh users pane.
                    onChange.accept(new ValueChange(true, false));
                })
                .owner(this)
                .taskMonitorFactory(view.getTaskMonitor())
                .exec();
    }

    private void updateAllPermissions() {
        if (allPermissions == null) {
            allPermissions = Arrays
                    .stream(AppPermission.values())
                    .sorted(Comparator.comparing(AppPermission::getDisplayValue))
                    .collect(Collectors.toList());
        }
        view.setRows(allPermissions);
    }

    private SafeHtml getDetails() {
        final DescriptionBuilder sb = new DescriptionBuilder();
        final AppPermission permission = view.getSelected();
        if (permission != null) {
            addPaths(
                    currentPermissions.getExplicitPermissions().contains(permission),
                    currentPermissions.getInheritedPermissions().get(permission),
                    sb,
                    "Explicit Permission",
                    "Inherited From:");

            // See if implied by administrator.
            if (!AppPermission.ADMINISTRATOR.equals(permission)) {
                addPaths(
                        currentPermissions
                                .getExplicitPermissions()
                                .contains(AppPermission.ADMINISTRATOR),
                        currentPermissions
                                .getInheritedPermissions()
                                .get(AppPermission.ADMINISTRATOR),
                        sb,
                        "Implied By Administrator",
                        "Implied By Administrator Inherited From:");
            }

            if (sb.toSafeHtml().asString().isEmpty()) {
                sb.addTitle("No Permission");
            }
        } else {
            sb.addLine("No application permission selected");
        }

        return sb.toSafeHtml();
    }

    private void addPaths(final boolean direct,
                          final List<String> paths,
                          final DescriptionBuilder sb,
                          final String directTitle,
                          final String inheritedTitle) {
        if (direct) {
            sb.addNewLine();
            sb.addNewLine();
            sb.addTitle(directTitle);
        }

        if (NullSafe.hasItems(paths)) {
            sb.addNewLine();
            sb.addNewLine();
            sb.addTitle(inheritedTitle);
            for (final String path : paths) {
                sb.addNewLine();
                sb.addLine(path);
            }
        }
    }


    // --------------------------------------------------------------------------------


    /// What a change means its owner should refresh.
    @SuppressWarnings("ClassCanBeRecord") // GWT :-(
    public static class ValueChange {

        private final boolean refreshUsers;
        private final boolean refreshDetails;

        /// @param refreshUsers   Whether to refresh the users, e.g. their permissions changed.
        /// @param refreshDetails Whether to refresh the details.
        public ValueChange(final boolean refreshUsers,
                           final boolean refreshDetails) {
            this.refreshUsers = refreshUsers;
            this.refreshDetails = refreshDetails;
        }

        /// @return Whether to refresh the users.
        public boolean isRefreshUsers() {
            return refreshUsers;
        }

        /// @return Whether to refresh the details.
        public boolean isRefreshDetails() {
            return refreshDetails;
        }
    }
}
