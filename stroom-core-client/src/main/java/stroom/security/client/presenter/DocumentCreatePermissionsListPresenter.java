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
import stroom.explorer.client.presenter.DocumentTypeCache;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypes;
import stroom.security.client.presenter.DocumentCreatePermissionsListPresenter.DocumentCreatePermissionsListView;
import stroom.security.shared.Changes;
import stroom.svg.client.Preset;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.UserRef;
import stroom.widget.util.client.CheckListSelectionEventManager;
import stroom.widget.util.client.MySingleSelectionModel;

import com.google.gwt.cell.client.SafeHtmlCell;
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
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;

public class DocumentCreatePermissionsListPresenter
        extends MyPresenterWidget<DocumentCreatePermissionsListView> {

    public static final int CHECKBOX_COL_WIDTH = 26;
    public static final int ICON_COL_WIDTH = 26;

    private final CellTable<DocumentType> cellTable;

    private final DocumentTypeCache documentTypeCache;
    private DocumentTypes documentTypes;
    private Changes changes;
    private UserRef currentUser;
    private UserRef currentOwner;
    private Set<String> currentPermissions;

    @Inject
    public DocumentCreatePermissionsListPresenter(final EventBus eventBus,
                                                  final DocumentCreatePermissionsListView view,
                                                  final DocumentTypeCache documentTypeCache) {
        super(eventBus, view);
        this.documentTypeCache = documentTypeCache;

        refreshDocTypeIcons();

        final boolean updatable = true;
        final Appearance appearance = updatable
                ? new TickBoxCell.DefaultAppearance()
                : new TickBoxCell.NoBorderAppearance();

        cellTable = new MyCellTable<>(MyDataGrid.DEFAULT_LIST_PAGE_SIZE);

        // Selection.
        final Column<DocumentType, TickBoxState> selectionColumn = buildSelectionColumn(
                updatable,
                appearance);
        selectionColumn.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        cellTable.addColumn(selectionColumn);
        cellTable.setColumnWidth(selectionColumn, CHECKBOX_COL_WIDTH, Unit.PX);


        // These are doc create perms so add a col to show the doc type icon
        final Column<DocumentType, Preset> typeColumn = DataGridUtil.svgPresetColumnBuilder(
                false,
                this::getDocTypeIcon).build();
        typeColumn.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        cellTable.addColumn(typeColumn);
        cellTable.setColumnWidth(typeColumn, ICON_COL_WIDTH, Unit.PX);

        // Permission Name
        final Column<DocumentType, SafeHtml> permissionNameColumn = buildPermissionNameColumn();
        cellTable.addColumn(permissionNameColumn);
        // Make this col variable width, using 100% of the space not used by the fixed width cols
        cellTable.setColumnWidth(permissionNameColumn, 100, Unit.PCT);

        if (updatable) {
            final int mouseMove = Event.getTypeInt(BrowserEvents.MOUSEMOVE);
            cellTable.sinkEvents(mouseMove);
            final MySingleSelectionModel<DocumentType> selectionModel = new MySingleSelectionModel<>();
            final PermissionsListSelectionEventManager selectionEventManager =
                    new PermissionsListSelectionEventManager(cellTable);
            cellTable.setSelectionModel(selectionModel, selectionEventManager);
        }

        cellTable.setWidth("auto", true);
        view.setTable(cellTable);
    }

    private boolean hasPermission(DocumentType documentType) {
        return currentPermissions.contains(documentType.getType());
    }

    private Column<DocumentType, TickBoxState> buildSelectionColumn(final boolean updatable,
                                                                    final Appearance appearance) {
        final Column<DocumentType, TickBoxState> selectionColumn = new Column<DocumentType, TickBoxState>(
                TickBoxCell.create(appearance, false, false, updatable)) {
            @Override
            public TickBoxState getValue(final DocumentType documentType) {
                TickBoxState tickBoxState = TickBoxState.UNTICK;
                if (hasPermission(documentType)) {
                    tickBoxState = TickBoxState.TICK;
                } else if (isInferred(documentType)) {
                    tickBoxState = TickBoxState.HALF_TICK;
                }

                return tickBoxState;
            }
        };
        selectionColumn.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_RIGHT);
        return selectionColumn;
    }

    private boolean isInferred(final DocumentType documentType) {
        // See if the permission is inferred.
        // TODO : We need to find a way of showing permissions that are inferred by the user belonging to
        //  the group that owns the item or belonging to a group that has the permission.
        if (Objects.equals(currentOwner, currentUser)) {
            return true;
        }
        return false;
    }

    private Column<DocumentType, SafeHtml> buildPermissionNameColumn() {
        final Column<DocumentType, SafeHtml> column = new Column<DocumentType, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final DocumentType documentType) {
                final boolean inferred = isInferred(documentType);
//                GWT.log(permission + " - " + optInferredType);
                final String permClassBase = "documentPermissionType-";
                final List<String> classes = new ArrayList<>();

                if (!hasPermission(documentType) && !inferred) {
                    classes.add(permClassBase + "noPermission");
                } else {
                    // User has this perm (possibly inferred)
                    classes.add(permClassBase + (inferred
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
                        .appendEscaped(documentType.getDisplayType())
                        .appendHtmlConstant("</span>")
                        .toSafeHtml();
            }
        };
        return column;
    }

    public void addDocumentCreatePermission(final DocumentType documentType) {
        changes.addDocumentCreatePermission(currentUser, documentType.getType());
    }

    public void removeDocumentCreatePermission(final DocumentType documentType) {
        changes.removeDocumentCreatePermission(currentUser, documentType.getType());
    }

    private void refreshDocTypeIcons() {
        // Hold map of doc type icons keyed on type to save constructing for each row
        documentTypeCache.fetch(documentTypes -> {
            this.documentTypes = documentTypes;
            refresh();
        }, this);
    }

    public void toggle(final DocumentType documentType) {
        // Determine if it is present in the model
        boolean hasPermission = false;
        if (currentPermissions != null) {
            hasPermission = currentPermissions.contains(documentType.getType());
        }

//        GWT.log("toggle, userUuid: " + userUuid
//                + ", permission: '" + permission
//                + ", hasPermission: " + hasPermission);

        if (hasPermission) {
            removeDocumentCreatePermission(documentType);
        } else {
            addDocumentCreatePermission(documentType);
        }
    }

    private void toggleSelectAll() {
        if (currentUser != null) {
            final boolean select = currentPermissions == null || currentPermissions.size() <
                    documentTypes.getTypes().size();
            for (final DocumentType documentType : documentTypes.getTypes()) {
                boolean hasPermission = false;
                if (currentPermissions != null) {
                    hasPermission = currentPermissions.contains(documentType.getType());
                }

                if (select) {
                    if (!hasPermission) {
                        addDocumentCreatePermission(documentType);
                    }
                } else {
                    if (hasPermission) {
                        removeDocumentCreatePermission(documentType);
                    }
                }
            }
            refresh();
        }
    }

    public void setDocumentPermissions(final Set<String> documentPermissions,
                                       final Changes changes) {
        this.currentPermissions = documentPermissions;
        this.changes = changes;
    }

    public void setCurrentOwner(final UserRef currentOwner) {
        this.currentOwner = currentOwner;
    }

    public void setCurrentUser(UserRef currentUser) {
        this.currentUser = currentUser;
    }

    private void refresh() {
        if (currentUser != null && documentTypes != null) {
            cellTable.setRowData(0, documentTypes.getTypes());
            cellTable.setRowCount(documentTypes.getTypes().size());
        } else {
            cellTable.setRowCount(0);
        }
    }

    private Preset getDocTypeIcon(final DocumentType documentType) {
        if (documentType != null) {
            return new Preset(documentType.getIcon(), documentType.getDisplayType(), true);
        } else {
            return null;
        }
    }

    // --------------------------------------------------------------------------------


    public interface DocumentCreatePermissionsListView extends View {

        void setTable(Widget widget);
    }


    // --------------------------------------------------------------------------------


    private class PermissionsListSelectionEventManager extends CheckListSelectionEventManager<DocumentType> {

        public PermissionsListSelectionEventManager(final AbstractHasData<DocumentType> cellTable) {
            super(cellTable);
        }

        @Override
        protected void onToggle(final DocumentType item) {
            toggle(item);
        }

        @Override
        protected void onSelectAll(final CellPreviewEvent<DocumentType> e) {
            toggleSelectAll();
        }
    }
}
