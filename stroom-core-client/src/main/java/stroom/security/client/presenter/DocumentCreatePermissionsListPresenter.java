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
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.grid.client.DataGridSelectionEventManager;
import stroom.data.grid.client.MyDataGrid;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.DocumentTypeCache;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerResource;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.DocumentCreatePermissionsListPresenter.DocumentCreatePermissionsListView;
import stroom.security.shared.AbstractDocumentPermissionsChange;
import stroom.security.shared.AppPermission;
import stroom.security.shared.BulkDocumentPermissionChangeRequest;
import stroom.security.shared.DocPermissionResource;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentPermissionFields;
import stroom.security.shared.DocumentUserPermissionsReport;
import stroom.security.shared.DocumentUserPermissionsRequest;
import stroom.svg.client.Preset;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.UserRef;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import javax.inject.Inject;

public class DocumentCreatePermissionsListPresenter
        extends MyPresenterWidget<DocumentCreatePermissionsListView> {

    private static final DocPermissionResource DOC_PERMISSION_RESOURCE = GWT.create(DocPermissionResource.class);
    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    public static final int CHECKBOX_COL_WIDTH = 26;
    public static final int ICON_COL_WIDTH = 26;

    private final ClientSecurityContext securityContext;
    private final MyDataGrid<DocumentType> dataGrid;
    private final MultiSelectionModelImpl<DocumentType> selectionModel;
    private final DataGridSelectionEventManager<DocumentType> selectionEventManager;
    private final RestFactory restFactory;

    private final DocumentTypeCache documentTypeCache;
    private DocumentTypes documentTypes;
//    private Set<String> documentPermissions = new HashSet<>();

    private DocumentUserPermissionsReport currentPermissions;
    private UserRef relatedUser;
    private DocRef relatedDoc;

    @Inject
    public DocumentCreatePermissionsListPresenter(final EventBus eventBus,
                                                  final DocumentCreatePermissionsListView view,
                                                  final DocumentTypeCache documentTypeCache,
                                                  final ClientSecurityContext securityContext,
                                                  final RestFactory restFactory) {
        super(eventBus, view);
        this.documentTypeCache = documentTypeCache;
        this.securityContext = securityContext;
        this.restFactory = restFactory;

        refreshDocTypeIcons();

        final boolean updatable = true;
        final Appearance appearance = updatable
                ? new TickBoxCell.DefaultAppearance()
                : new TickBoxCell.NoBorderAppearance();

        dataGrid = new MyDataGrid<>();
        selectionModel = new MultiSelectionModelImpl<>(dataGrid);
        selectionEventManager = new DataGridSelectionEventManager<>(dataGrid, selectionModel, false);
        dataGrid.setSelectionModel(selectionModel, selectionEventManager);
        view.setTable(dataGrid);
        addColumns();
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
            addPaths(
                    GwtNullSafe.set(GwtNullSafe.get(currentPermissions,
                                    DocumentUserPermissionsReport::getExplicitCreatePermissions))
                            .contains(documentType.getType()),
                    GwtNullSafe.map(GwtNullSafe.get(currentPermissions,
                                    DocumentUserPermissionsReport::getInheritedCreatePermissionPaths))
                            .get(documentType.getType()),
                    sb,
                    "Explicit Permission",
                    "Inherited Permission From:");

            // See if implied by ownership.
            addPaths(
                    DocumentPermission.OWNER.equals(currentPermissions.getExplicitPermission()),
                    GwtNullSafe.map(GwtNullSafe.get(currentPermissions,
                                    DocumentUserPermissionsReport::getInheritedPermissionPaths))
                            .get(DocumentPermission.OWNER),
                    sb,
                    "Implied By Ownership",
                    "Inherited Permission (Implied By Ownership) From:");

            if (sb.toSafeHtml().asString().length() == 0) {
                sb.addTitle("No Permission");
            }
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

        if (paths != null && paths.size() > 0) {
            sb.addNewLine();
            sb.addNewLine();
            sb.addTitle(inheritedTitle);
            for (final String path : paths) {
                sb.addNewLine();
                sb.addLine(path);
            }
        }
    }

    private void addColumns() {
        final boolean updateable = isCurrentUserUpdate();
        final TickBoxCell.Appearance appearance = updateable
                ? new TickBoxCell.DefaultAppearance()
                : new TickBoxCell.NoBorderAppearance();

        // Selection
        final Column<DocumentType, TickBoxState> selectionColumn = new Column<DocumentType, TickBoxState>(
                TickBoxCell.create(appearance, true, true, updateable)) {
            @Override
            public TickBoxState getValue(final DocumentType documentType) {
                if (currentPermissions != null) {
                    if (currentPermissions.getExplicitCreatePermissions() != null &&
                            currentPermissions
                                    .getExplicitCreatePermissions()
                                    .contains(documentType.getType())) {
                        return TickBoxState.TICK;
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
        };

        if (updateable) {
            selectionColumn.setFieldUpdater((index, permission, value) -> {
                onChange(permission, TickBoxState.TICK.equals(value));
            });
        }

        dataGrid.addColumn(selectionColumn, "<br/>", ColumnSizeConstants.ICON_COL);

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

    //    private void addDocumentCreatePermission(final DocumentType documentType) {
//        documentPermissions.add(documentType.getType());
//    }
//
//    private void removeDocumentCreatePermission(final DocumentType documentType) {
//        documentPermissions.remove(documentType.getType());
//    }
//
    private void refreshDocTypeIcons() {
        // Hold map of doc type icons keyed on type to save constructing for each row
        documentTypeCache.fetch(documentTypes -> {
            this.documentTypes = documentTypes;
            refresh();
        }, this);
    }
//
//    private void toggle(final DocumentType documentType) {
//        // Determine if it is present in the model
//        boolean hasPermission = false;
//        if (documentPermissions != null) {
//            hasPermission = documentPermissions.contains(documentType.getType());
//        }
//
////        GWT.log("toggle, userUuid: " + userUuid
////                + ", permission: '" + permission
////                + ", hasPermission: " + hasPermission);
//
//        if (hasPermission) {
//            removeDocumentCreatePermission(documentType);
//        } else {
//            addDocumentCreatePermission(documentType);
//        }
//    }
//
//    private void toggleSelectAll() {
//        final boolean select = documentPermissions == null || documentPermissions.size() <
//                documentTypes.getTypes().size();
//        for (final DocumentType documentType : documentTypes.getTypes()) {
//            boolean hasPermission = false;
//            if (documentPermissions != null) {
//                hasPermission = documentPermissions.contains(documentType.getType());
//            }
//
//            if (select) {
//                if (!hasPermission) {
//                    addDocumentCreatePermission(documentType);
//                }
//            } else {
//                if (hasPermission) {
//                    removeDocumentCreatePermission(documentType);
//                }
//            }
//        }
//        refresh();
//    }

//    private void setCurrentPermissions(final DocumentUserPermissionsReport currentPermissions) {
//        this.currentPermissions = currentPermissions;
////        documentPermissions.clear();
////        if (currentPermissions != null) {
////            documentPermissions.addAll(currentPermissions.getExplicitCreatePermissions());
////        }
//    }

//    private Set<String> getDocumentPermissions() {
//        return documentPermissions;
//    }

    private void refresh() {
        if (documentTypes != null) {
            dataGrid.setRowData(0, documentTypes.getTypes());
            dataGrid.setRowCount(documentTypes.getTypes().size());
        } else {
            dataGrid.setRowCount(0);
        }
        updateDetails();
    }

    private Preset getDocTypeIcon(final DocumentType documentType) {
        if (documentType != null) {
            return new Preset(documentType.getIcon(), documentType.getDisplayType(), true);
        } else {
            return null;
        }
    }

    public void setup(final UserRef relatedUser,
                      final DocRef relatedDoc,
                      final DocumentUserPermissionsReport permissions) {
        this.relatedUser = relatedUser;
        this.relatedDoc = relatedDoc;
        this.currentPermissions = permissions;

        refresh();

//        if (relatedUser == null || relatedDoc == null) {
//            setCurrentPermissions(null);
//            refresh();
//
//        } else {
//            // Fetch permissions and populate table.
//            final DocumentUserPermissionsRequest request = new DocumentUserPermissionsRequest(relatedDoc, relatedUser);
//            restFactory
//                    .create(DOC_PERMISSION_RESOURCE)
//                    .method(res -> res.getDocUserPermissionsReport(request))
//                    .onSuccess(response -> {
//                        currentPermissions = response;
//                        setCurrentPermissions(currentPermissions);
//                        refresh();
//                    })
//                    .taskListener(this)
//                    .exec();
//        }
    }

    public void onChange(final DocumentType documentType, final boolean selected) {
        if (relatedUser != null) {
            final AbstractDocumentPermissionsChange change;
            if (selected) {
                change = new AbstractDocumentPermissionsChange
                        .AddDocumentCreatePermission(
                        relatedUser,
                        documentType);
            } else {
                change = new AbstractDocumentPermissionsChange
                        .RemoveDocumentCreatePermission(
                        relatedUser,
                        documentType);
            }

            final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.OR);
            builder.addDocRefTerm(DocumentPermissionFields.DOCUMENT, Condition.IS_DOC_REF, relatedDoc);
            if (getView().isIncludeDescendants()) {
                builder.addTerm(ExpressionTerm.builder()
                        .field(DocumentPermissionFields.DESCENDANTS)
                        .condition(Condition.OF_DOC_REF)
                        .docRef(relatedDoc)
                        .build());
            }
            final ExpressionOperator expression = builder.build();

            final BulkDocumentPermissionChangeRequest request = new BulkDocumentPermissionChangeRequest(expression,
                    change);
            restFactory
                    .create(EXPLORER_RESOURCE)
                    .method(res -> res.changeDocumentPermssions(request))
                    .onSuccess(response -> afterChange())
                    .taskListener(this)
                    .exec();
        }
    }

    private void afterChange() {
        final DocumentUserPermissionsRequest request = new DocumentUserPermissionsRequest(relatedDoc, relatedUser);
        restFactory
                .create(DOC_PERMISSION_RESOURCE)
                .method(res -> res.getDocUserPermissionsReport(request))
                .onSuccess(response -> setup(relatedUser, relatedDoc, response))
                .taskListener(this)
                .exec();
    }

    public boolean isIncludeDescendants() {
        return getView().isIncludeDescendants();
    }

    public interface DocumentCreatePermissionsListView extends View {

        void setTable(Widget widget);

        void setDetails(SafeHtml details);

        boolean isIncludeDescendants();
    }
}
