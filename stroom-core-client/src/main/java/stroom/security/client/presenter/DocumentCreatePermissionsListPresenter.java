/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.security.client.presenter;

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.grid.client.DataGridSelectionEventManager;
import stroom.data.grid.client.MyDataGrid;
import stroom.docstore.shared.DocumentType;
import stroom.explorer.client.presenter.DocumentTypeCache;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerConstants;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.DocumentCreatePermissionsListPresenter.DocumentCreatePermissionsListView;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentUserPermissionsReport;
import stroom.svg.client.Preset;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

public class DocumentCreatePermissionsListPresenter
        extends MyPresenterWidget<DocumentCreatePermissionsListView> {

    private final ClientSecurityContext securityContext;
    private final MyDataGrid<DocumentType> dataGrid;
    private final MultiSelectionModelImpl<DocumentType> selectionModel;

    private final DocumentTypeCache documentTypeCache;

    private DocumentUserPermissionsReport currentPermissions;

    private Set<String> explicitCreatePermissions;

    @Inject
    public DocumentCreatePermissionsListPresenter(final EventBus eventBus,
                                                  final DocumentCreatePermissionsListView view,
                                                  final DocumentTypeCache documentTypeCache,
                                                  final ClientSecurityContext securityContext) {
        super(eventBus, view);
        this.documentTypeCache = documentTypeCache;
        this.securityContext = securityContext;

        dataGrid = new MyDataGrid<>(this);
        selectionModel = new MultiSelectionModelImpl<>();
        final DataGridSelectionEventManager<DocumentType> selectionEventManager =
                new DataGridSelectionEventManager<>(dataGrid, selectionModel, false);
        dataGrid.setSelectionModel(selectionModel, selectionEventManager);
        view.setTable(dataGrid);

        // Hold map of doc type icons keyed on type to save constructing for each row
        documentTypeCache.fetch(documentTypes -> {
            addColumns(documentTypes);
            refresh(documentTypes);
        }, this);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(selectionModel.addSelectionHandler(e ->
                updateDetails()));
    }

    private void updateDetails() {
        final SafeHtml details = getDetails();
        getView().setDetails(details);
    }

    private SafeHtml getDetails() {
        final DescriptionBuilder sb = new DescriptionBuilder();
        final DocumentType documentType = selectionModel.getSelected();
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

    private TickBoxState getPermissionState(final DocumentType documentType) {
        if (currentPermissions != null) {
            if (explicitCreatePermissions != null &&
                explicitCreatePermissions
                        .contains(documentType.getType())) {
                return TickBoxState.TICK;
            } else if (explicitCreatePermissions != null &&
                       explicitCreatePermissions
                               .contains(ExplorerConstants.ALL_CREATE_PERMISSIONS)) {
                return TickBoxState.HALF_TICK;
            } else if (currentPermissions.getExplicitPermission() != null &&
                       currentPermissions
                               .getExplicitPermission()
                               .equals(DocumentPermission.OWNER)) {
                return TickBoxState.HALF_TICK;
            } else if (currentPermissions.getInheritedPermissionPaths() != null &&
                       currentPermissions
                               .getInheritedPermissionPaths()
                               .containsKey(DocumentPermission.OWNER)) {
                return TickBoxState.HALF_TICK;
            } else if (currentPermissions.getInheritedCreatePermissionPaths() != null &&
                       currentPermissions
                               .getInheritedCreatePermissionPaths()
                               .containsKey(documentType.getType())) {
                return TickBoxState.HALF_TICK;
            }
        }
        return TickBoxState.UNTICK;
    }

    private void addColumns(final DocumentTypes documentTypes) {
        final boolean updateable = isCurrentUserUpdate();
        final TickBoxCell.Appearance appearance = updateable
                ? new TickBoxCell.DefaultAppearance()
                : new TickBoxCell.NoBorderAppearance();

        // Selection
        final Column<DocumentType, TickBoxState> selectionColumn = new Column<DocumentType, TickBoxState>(
                TickBoxCell.create(appearance, true, true, updateable)) {
            @Override
            public TickBoxState getValue(final DocumentType documentType) {
                return getPermissionState(documentType);
            }
        };

        if (updateable) {
            final Header<TickBoxState> header = new Header<TickBoxState>(
                    TickBoxCell.create(false, false)) {
                @Override
                public TickBoxState getValue() {
                    final boolean all = NullSafe.set(explicitCreatePermissions)
                            .contains(ExplorerConstants.ALL_CREATE_PERMISSIONS);
                    if (all) {
                        return TickBoxState.TICK;
                    }

                    for (final DocumentType documentType : documentTypes.getTypes()) {
                        final TickBoxState state = getPermissionState(documentType);
                        if (!TickBoxState.UNTICK.equals(state)) {
                            return TickBoxState.HALF_TICK;
                        }
                    }

                    return TickBoxState.UNTICK;
                }
            };
            header.setUpdater(value -> {
                if (value.equals(TickBoxState.UNTICK)) {
                    explicitCreatePermissions.remove(ExplorerConstants.ALL_CREATE_PERMISSIONS);
                    refresh();
                } else if (value.equals(TickBoxState.TICK)) {
                    explicitCreatePermissions.add(ExplorerConstants.ALL_CREATE_PERMISSIONS);
                    refresh();
                }
            });
            selectionColumn.setFieldUpdater((index, permission, value) -> {
                if (TickBoxState.TICK.equals(value)) {
                    explicitCreatePermissions.add(permission.getType());
                } else {
                    explicitCreatePermissions.remove(permission.getType());
                }
                refresh();
            });
            dataGrid.addColumn(selectionColumn, header, ColumnSizeConstants.CHECKBOX_COL);
        } else {
            dataGrid.addColumn(selectionColumn, "<br/>", ColumnSizeConstants.ICON_COL);
        }

        // Icon
        final Column<DocumentType, Preset> iconColumn = DataGridUtil.svgPresetColumnBuilder(
                false,
                this::getDocTypeIcon).build();
        iconColumn.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        dataGrid.addColumn(iconColumn, "<br/>", ColumnSizeConstants.ICON_COL);

        // Name
        dataGrid.addAutoResizableColumn(new Column<DocumentType, String>(new TextCell()) {
            @Override
            public String getValue(final DocumentType documentType) {
                return documentType.getDisplayType();
            }
        }, "Document Type", 200);
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
        documentTypeCache.fetch(this::refresh, this);
    }

    private void refresh(final DocumentTypes documentTypes) {
        if (documentTypes != null) {
            dataGrid.setRowData(0, documentTypes.getTypes());
            dataGrid.setRowCount(documentTypes.getTypes().size());
        } else {
            dataGrid.setRowCount(0);
        }
        updateDetails();
    }

    public void setup(final DocumentUserPermissionsReport permissions) {
        this.currentPermissions = permissions;
        explicitCreatePermissions = new HashSet<>();
        explicitCreatePermissions.addAll(permissions.getExplicitCreatePermissions());

        refresh();
    }

    public Set<String> getExplicitCreatePermissions() {
        return explicitCreatePermissions;
    }

    private Preset getDocTypeIcon(final DocumentType documentType) {
        if (documentType != null) {
            return new Preset(documentType.getIcon(), documentType.getDisplayType(), true);
        }
        return null;
    }

    public interface DocumentCreatePermissionsListView extends View {

        void setTable(Widget widget);

        void setDetails(SafeHtml details);
    }
}
