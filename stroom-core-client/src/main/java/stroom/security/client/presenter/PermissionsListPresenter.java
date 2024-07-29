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

package stroom.security.client.presenter;

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.client.TickBoxCell.Appearance;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.table.client.MyCellTable;
import stroom.dispatch.client.RestFactory;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.ExplorerResource;
import stroom.security.shared.Changes;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.DocumentPermissionNames.InferredPermissionType;
import stroom.security.shared.DocumentPermissionNames.InferredPermissions;
import stroom.security.shared.DocumentPermissionNames.PermissionType;
import stroom.security.shared.DocumentPermissions;
import stroom.security.shared.User;
import stroom.svg.client.Preset;
import stroom.svg.shared.SvgImage;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.util.client.CheckListSelectionEventManager;
import stroom.widget.util.client.MySingleSelectionModel;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.cellview.client.AbstractHasData;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.CellPreviewEvent;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class PermissionsListPresenter
        extends MyPresenterWidget<PermissionsListPresenter.PermissionsListView> {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);
    public static final int CHECKBOX_COL_WIDTH = 26;
    public static final int ICON_COL_WIDTH = 26;

    private final CellTable<String> cellTable;
    private Map<String, SvgImage> typeToSvgMap = new HashMap<>();

    private DocumentPermissions documentPermissions;
    private List<String> allPermissions;
    private InferredPermissions inferredPermissions = InferredPermissions.empty();
    private Changes changes;
    private User currentUser;
    private final RestFactory restFactory;

    private Column<String, Preset> typeColumn = null;

    @Inject
    public PermissionsListPresenter(final EventBus eventBus,
                                    final PermissionsListView view,
                                    final RestFactory restFactory) {
        super(eventBus, view);
        this.restFactory = restFactory;

        refreshDocTypeIcons();

        final boolean updatable = true;
        final Appearance appearance = updatable
                ? new TickBoxCell.DefaultAppearance()
                : new TickBoxCell.NoBorderAppearance();

        cellTable = new MyCellTable<>(MyDataGrid.DEFAULT_LIST_PAGE_SIZE);

        // Selection.
        final Column<String, TickBoxState> selectionColumn = buildSelectionColumn(
                updatable,
                appearance);
        selectionColumn.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        cellTable.addColumn(selectionColumn);
        cellTable.setColumnWidth(selectionColumn, CHECKBOX_COL_WIDTH, Unit.PX);

        // Permission Name
        final Column<String, SafeHtml> permissionNameColumn = buildPermissionNameColumn();
        cellTable.addColumn(permissionNameColumn);
        // Make this col variable width, using 100% of the space not used by the fixed width cols
        cellTable.setColumnWidth(permissionNameColumn, 100, Unit.PCT);

        if (updatable) {
            final int mouseMove = Event.getTypeInt(BrowserEvents.MOUSEMOVE);
            cellTable.sinkEvents(mouseMove);
            final MySingleSelectionModel<String> selectionModel = new MySingleSelectionModel<>();
            final PermissionsListSelectionEventManager selectionEventManager =
                    new PermissionsListSelectionEventManager(cellTable);
            cellTable.setSelectionModel(selectionModel, selectionEventManager);
        }

        cellTable.setWidth("auto", true);
        view.setTable(cellTable);
    }

    private Column<String, TickBoxState> buildSelectionColumn(final boolean updatable,
                                                              final Appearance appearance) {
        final Column<String, TickBoxState> selectionColumn = new Column<String, TickBoxState>(
                TickBoxCell.create(appearance, false, false, updatable)) {
            @Override
            public TickBoxState getValue(final String permission) {
                final Optional<InferredPermissionType> optPermType = getInferredPermissions()
                        .getInferredPermissionType(permission);

                return optPermType.map(type -> type.isDirect()
                                ? TickBoxState.TICK
                                : TickBoxState.HALF_TICK)
                        .orElse(TickBoxState.UNTICK);
            }
        };
        selectionColumn.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_RIGHT);
        return selectionColumn;
    }

    private Column<String, SafeHtml> buildPermissionNameColumn() {
        final Column<String, SafeHtml> column = new Column<String, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final String permission) {
                final Optional<InferredPermissionType> optInferredType = getInferredPermissions()
                        .getInferredPermissionType(permission);
//                GWT.log(permission + " - " + optInferredType);
                final String permClassBase = "documentPermissionType-";
                final List<String> classes = new ArrayList<>();

                if (optInferredType.isEmpty()) {
                    classes.add(permClassBase + "noPermission");
                } else {
                    // User has this perm (possibly inferred)
                    if (!DocumentPermissionNames.isDocumentCreatePermission(permission)) {
                        final PermissionType permissionType = DocumentPermissionNames.getPermissionType(permission);
                        classes.add(permClassBase + (PermissionType.DESTRUCTIVE.equals(permissionType)
                                ? "destructive"
                                : "nonDestructive"));
                    }
                    final InferredPermissionType inferredType = optInferredType.get();
                    classes.add(permClassBase + (inferredType.isInferred()
                            ? "inferred"
                            : "direct"));
                }

                final SafeHtmlBuilder safeHtmlBuilder = new SafeHtmlBuilder()
                        .appendHtmlConstant("<span class=\"");
                for (int i = 0; i < classes.size(); i++) {
                    if (i > 0) {
                        safeHtmlBuilder.appendEscaped(" ");
                    }
                    safeHtmlBuilder.appendEscaped(classes.get(i));
                }
                return safeHtmlBuilder
                        .appendHtmlConstant("\">")
                        .appendEscaped(permission)
                        .appendHtmlConstant("</span>")
                        .toSafeHtml();
            }
        };
        return column;
    }

    private void updateInferredPermissions() {
        if (currentUser != null && documentPermissions != null) {
            final Set<String> directPermissions = documentPermissions.getPermissionsForUser(currentUser.getUuid());
            inferredPermissions = DocumentPermissionNames.getInferredPermissions(directPermissions);
        } else {
            inferredPermissions = InferredPermissions.empty();
        }
    }

    private void refreshDocTypeIcons() {
        // Hold map of doc type icons keyed on type to save constructing for each row
        restFactory
                .create(EXPLORER_RESOURCE)
                .method(ExplorerResource::fetchDocumentTypes)
                .onSuccess(documentTypes -> typeToSvgMap = documentTypes
                        .getTypes()
                        .stream()
                        .collect(Collectors.toMap(
                                DocumentType::getType,
                                DocumentType::getIcon)))
                .taskListener(this)
                .exec();
    }

    public void addPermission(final String userUuid, final String permission) {
        final Set<String> removals = GwtNullSafe.set(changes.getRemove().get(userUuid));
        // If this perm is in removals for the user/group, then they had it at the start
        // of this edit session, so no need to treat as an ADD, just remove the REMOVE.
        if (!removals.remove(permission)) {
            // Record the ADD
            changes.getAdd()
                    .computeIfAbsent(userUuid, k -> new HashSet<>())
                    .add(permission);
        }

        // Add to the model.
        documentPermissions.addPermission(userUuid, permission);
        updateInferredPermissions();
//        GWT.log("After ADD operation for userUuid: " + userUuid
//                + ", permission: '" + permission
//                + "', permissions:\n"
//                + DocumentPermissions.permsMapToStr(documentPermissions.getPermissions())
//                + "\n" + changes.toString());
    }

    public void removePermission(final String userUuid, final String permission) {
        final Set<String> additions = GwtNullSafe.set(changes.getAdd().get(userUuid));
        // If this perm is in removals for the user/group, then they had it at the start
        // of this edit session, so no need to treat as an ADD, just remove the REMOVE.
        if (!additions.remove(permission)) {
            // Record the REMOVE
            changes.getRemove()
                    .computeIfAbsent(userUuid, k -> new HashSet<>())
                    .add(permission);
        }

        // Remove from the model.
        documentPermissions.removePermission(userUuid, permission);
        updateInferredPermissions();
//        GWT.log("After REMOVE operation for userUuid: " + userUuid
//                + ", permission: '" + permission
//                + "', permissions:\n"
//                + DocumentPermissions.permsMapToStr(documentPermissions.getPermissions())
//                + "\n" + changes.toString());
    }

    public void toggle(final String userUuid, final String permission) {
        // Determine if it is present in the model
        boolean hasPermission = false;
        final Set<String> permissions = documentPermissions.getPermissions().get(userUuid);
        if (permissions != null) {
            hasPermission = permissions.contains(permission);
        }

//        GWT.log("toggle, userUuid: " + userUuid
//                + ", permission: '" + permission
//                + ", hasPermission: " + hasPermission);

        if (hasPermission) {
            removePermission(userUuid, permission);
        } else {
            addPermission(userUuid, permission);
        }
    }

    private void toggle(final String permission) {
        if (permission != null && currentUser != null) {
            final String userUuid = currentUser.getUuid();
            toggle(userUuid, permission);
            refresh();
        }
    }

    private void toggleSelectAll() {
        if (currentUser != null) {
            final String userUuid = currentUser.getUuid();
            final Set<String> currentPermissions = documentPermissions.getPermissions().get(userUuid);
            final boolean select = currentPermissions == null || currentPermissions.size() < allPermissions.size();

            for (final String permission : allPermissions) {
                boolean hasPermission = false;
                if (currentPermissions != null) {
                    hasPermission = currentPermissions.contains(permission);
                }

                if (select) {
                    if (!hasPermission) {
                        addPermission(userUuid, permission);
                    }
                } else {
                    if (hasPermission) {
                        removePermission(userUuid, permission);
                    }
                }
            }
            refresh();
        }
    }

    public void setDocumentPermissions(final DocumentPermissions documentPermissions,
                                       final List<String> allPermissions,
                                       final Changes changes) {
        this.documentPermissions = documentPermissions;
        this.allPermissions = allPermissions;
        this.changes = changes;
        updateInferredPermissions();

        final boolean containsCreatePerms = GwtNullSafe.stream(allPermissions)
                .anyMatch(DocumentPermissionNames::isDocumentCreatePermission);

        if (containsCreatePerms) {
            // These are doc create perms so add a col to show the doc type icon
            if (typeColumn == null) {
                typeColumn = DataGridUtil.svgPresetColumnBuilder(
                                false,
                                this::getDocTypeIcon)
                        .build();
                typeColumn.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);
            }
            if (typeColumn == null || cellTable.getColumnIndex(typeColumn) == -1) {
                cellTable.insertColumn(1, typeColumn);
                cellTable.setColumnWidth(typeColumn, ICON_COL_WIDTH, Unit.PX);
            }
        } else {
            // Not doc create perms so remove the col
            if (cellTable.getColumnIndex(typeColumn) != -1) {
                cellTable.removeColumn(typeColumn);
            }
        }
    }

    private Preset getDocTypeIcon(final String permission) {
        if (!GwtNullSafe.isBlankString(permission)) {
            final String type = DocumentPermissionNames.getTypeFromDocumentCreatePermission(permission);
            return GwtNullSafe.get(
                    typeToSvgMap.get(type),
                    svgImage -> new Preset(svgImage, type, true));
        } else {
            return null;
        }
    }

    public void setCurrentUser(User currentUser) {
        this.currentUser = currentUser;
        updateInferredPermissions();
        refresh();
    }

    private void refresh() {
        if (currentUser != null) {
            cellTable.setRowData(0, allPermissions);
            cellTable.setRowCount(allPermissions.size());
        } else {
            cellTable.setRowCount(0);
        }
    }

    public InferredPermissions getInferredPermissions() {
        return inferredPermissions;
    }

    // --------------------------------------------------------------------------------


    public interface PermissionsListView extends View {

        void setTable(Widget widget);
    }


    // --------------------------------------------------------------------------------


    private class PermissionsListSelectionEventManager extends CheckListSelectionEventManager<String> {

        public PermissionsListSelectionEventManager(final AbstractHasData<String> cellTable) {
            super(cellTable);
        }

        @Override
        protected void onToggle(final String item) {
            toggle(item);
        }

        @Override
        protected void onSelectAll(final CellPreviewEvent<String> e) {
            toggleSelectAll();
        }
    }
}
