/*
 * Copyright 2024 Crown Copyright
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
import stroom.docstore.shared.DocumentType;
import stroom.explorer.client.DocumentTypeCache;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerConstants;
import stroom.security.client.DescriptionBuilder;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentUserPermissionsReport;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// The document types a user or group may create in a folder
/// ([DocumentCreatePermissionsList]): their permissions report is given with
/// [#setup(DocumentUserPermissionsReport)], the user ticks types (or 'Create All'), and the owner
/// reads [#getExplicitCreatePermissions()].
public class DocumentCreatePermissionsListController extends Controller {

    private final DocumentCreatePermissionsList view;
    private final ClientSecurityContext securityContext;
    private final DocumentTypeCache documentTypeCache;

    private DocumentUserPermissionsReport currentPermissions;
    private Set<String> explicitCreatePermissions;
    private List<DocumentType> documentTypes = List.of();
    private Runnable onChange = () -> {
    };

    /// Made by GIN, with its content.
    ///
    /// @param eventBus          The application's event bus.
    /// @param view              The content.
    /// @param documentTypeCache Gives the document types.
    /// @param securityContext   Says whether the user may manage users.
    @Inject
    public DocumentCreatePermissionsListController(final EventBus eventBus,
                                                   final DocumentCreatePermissionsList view,
                                                   final DocumentTypeCache documentTypeCache,
                                                   final ClientSecurityContext securityContext) {
        super(eventBus);
        this.view = view;
        this.documentTypeCache = documentTypeCache;
        this.securityContext = securityContext;
        initWidget(view);
        view.setPermissionState(documentType ->
                permissionState(currentPermissions, explicitCreatePermissions, documentType.getType()));
        view.setAllState(() -> allState(currentPermissions, explicitCreatePermissions, documentTypes));
        view.setOnTick(this::onTick);
        view.setOnTickAll(this::onTickAll);
        view.setOnSelectionChange(this::updateDetails);

        documentTypeCache.fetch(types -> {
            documentTypes = types.getTypes();
            view.addColumns(documentTypes, isCurrentUserUpdate());
            refresh(types);
        }, taskMonitor());
    }

    /// Shows a user's or group's create permissions.
    ///
    /// @param permissions Their permissions report for the folder.
    public void setup(final DocumentUserPermissionsReport permissions) {
        this.currentPermissions = permissions;
        explicitCreatePermissions = new HashSet<>();
        explicitCreatePermissions.addAll(permissions.getExplicitCreatePermissions());
        refresh();
    }

    /// @return The types (and 'Create All', [ExplorerConstants#ALL_CREATE_PERMISSIONS]) ticked.
    public Set<String> getExplicitCreatePermissions() {
        return explicitCreatePermissions;
    }

    /// @param onChange Run when a type, or 'Create All', is ticked or unticked.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /// The state of a type's tick box: ticked for an explicit permission, half ticked for one implied
    /// by 'Create All' or by ownership, or inherited, and unticked otherwise.
    ///
    /// @param report   The permissions report, or `null` before one is given.
    /// @param explicit The types (and 'Create All') explicitly permitted, or `null`.
    /// @param type     The document type.
    /// @return The tick box state.
    static TickBoxState permissionState(final DocumentUserPermissionsReport report,
                                        final Set<String> explicit,
                                        final String type) {
        if (report != null) {
            if (explicit != null && explicit.contains(type)) {
                return TickBoxState.TICK;
            } else if (explicit != null && explicit.contains(ExplorerConstants.ALL_CREATE_PERMISSIONS)) {
                return TickBoxState.HALF_TICK;
            } else if (DocumentPermission.OWNER.equals(report.getExplicitPermission())) {
                return TickBoxState.HALF_TICK;
            } else if (report.getInheritedPermissionPaths() != null
                       && report.getInheritedPermissionPaths().containsKey(DocumentPermission.OWNER)) {
                return TickBoxState.HALF_TICK;
            } else if (report.getInheritedCreatePermissionPaths() != null
                       && report.getInheritedCreatePermissionPaths().containsKey(type)) {
                return TickBoxState.HALF_TICK;
            }
        }
        return TickBoxState.UNTICK;
    }

    /// The state of the header's tick box: ticked for 'Create All', half ticked if any type's box is
    /// ticked or half ticked, and unticked otherwise.
    ///
    /// @param report        The permissions report, or `null` before one is given.
    /// @param explicit      The types (and 'Create All') explicitly permitted, or `null`.
    /// @param documentTypes Every document type.
    /// @return The tick box state.
    static TickBoxState allState(final DocumentUserPermissionsReport report,
                                 final Set<String> explicit,
                                 final List<DocumentType> documentTypes) {
        if (NullSafe.set(explicit).contains(ExplorerConstants.ALL_CREATE_PERMISSIONS)) {
            return TickBoxState.TICK;
        }
        for (final DocumentType documentType : documentTypes) {
            if (!TickBoxState.UNTICK.equals(permissionState(report, explicit, documentType.getType()))) {
                return TickBoxState.HALF_TICK;
            }
        }
        return TickBoxState.UNTICK;
    }

    private void onTick(final DocumentType documentType, final boolean ticked) {
        if (ticked) {
            explicitCreatePermissions.add(documentType.getType());
        } else {
            explicitCreatePermissions.remove(documentType.getType());
        }
        refresh();
        onChange.run();
    }

    private void onTickAll(final boolean ticked) {
        if (ticked) {
            explicitCreatePermissions.add(ExplorerConstants.ALL_CREATE_PERMISSIONS);
        } else {
            explicitCreatePermissions.remove(ExplorerConstants.ALL_CREATE_PERMISSIONS);
        }
        refresh();
        onChange.run();
    }

    private void updateDetails() {
        view.setDetails(getDetails());
    }

    private SafeHtml getDetails() {
        final DescriptionBuilder sb = new DescriptionBuilder();
        final DocumentType documentType = view.getSelected();
        if (documentType != null && currentPermissions != null) {

            // See if explicit permission.
            if (NullSafe.set(explicitCreatePermissions)
                    .contains(documentType.getType())) {
                addDirect(sb, "Explicit Permission");
            }

            // See if implied by 'Create All' permission.
            if (NullSafe.set(explicitCreatePermissions)
                    .contains(ExplorerConstants.ALL_CREATE_PERMISSIONS)) {
                addDirect(sb, "Implied By 'Create All' Permission");
            }

            // See if implied by ownership.
            if (DocumentPermission.OWNER.equals(currentPermissions.getExplicitPermission())) {
                addDirect(sb, "Implied By Ownership");
            }

            // See if implied by inherited 'Create All' permission.
            addPaths(
                    sb,
                    NullSafe.map(NullSafe.get(currentPermissions,
                                    DocumentUserPermissionsReport::getInheritedCreatePermissionPaths))
                            .get(ExplorerConstants.ALL_CREATE_PERMISSIONS),
                    "Implied By 'Create All' Permission Inherited From:");

            // See if inherited.
            addPaths(
                    sb,
                    NullSafe.map(NullSafe.get(currentPermissions,
                                    DocumentUserPermissionsReport::getInheritedCreatePermissionPaths))
                            .get(documentType.getType()),
                    "Inherited From:");

            // See if inherited and implied by ownership.
            addPaths(
                    sb,
                    NullSafe.map(NullSafe.get(currentPermissions,
                                    DocumentUserPermissionsReport::getInheritedPermissionPaths))
                            .get(DocumentPermission.OWNER),

                    "Implied By Ownership Inherited From:");

            if (sb.toSafeHtml().asString().length() == 0) {
                sb.addTitle("No Permission");
            }
        }

        return sb.toSafeHtml();
    }

    private void addDirect(final DescriptionBuilder sb,
                           final String message) {
        sb.addNewLine();
        sb.addNewLine();
        sb.addTitle(message);
    }

    private void addPaths(final DescriptionBuilder sb,
                          final List<String> paths,
                          final String message) {
        if (paths != null && paths.size() > 0) {
            sb.addNewLine();
            sb.addNewLine();
            sb.addTitle(message);
            for (final String path : paths) {
                sb.addNewLine();
                sb.addLine(path);
            }
        }
    }

    private boolean isCurrentUserUpdate() {
        if (securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)) {
            return true;
        }

        return currentPermissions != null &&
               (currentPermissions.getExplicitPermission().equals(DocumentPermission.OWNER) ||
                (currentPermissions.getInheritedPermissionPaths() != null &&
                 currentPermissions.getInheritedPermissionPaths().containsKey(DocumentPermission.OWNER)
                )
               );
    }

    private void refresh() {
        documentTypeCache.fetch(this::refresh, taskMonitor());
    }

    private void refresh(final DocumentTypes types) {
        view.setRows(NullSafe.get(types, DocumentTypes::getTypes));
        updateDetails();
    }

}
