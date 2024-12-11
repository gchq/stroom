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
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.DocumentTypeCache;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerConstants;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.DocumentCreatePermissionsListPresenter.DocumentCreatePermissionsListView;
import stroom.security.shared.AbstractDocumentPermissionsChange;
import stroom.security.shared.AppPermission;
import stroom.security.shared.BulkDocumentPermissionChangeRequest;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentPermissionFields;
import stroom.security.shared.DocumentUserPermissionsReport;
import stroom.svg.client.Preset;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.UserRef;
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

import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;

public class DocumentCreatePermissionsListPresenter
        extends MyPresenterWidget<DocumentCreatePermissionsListView> {

    public static final int CHECKBOX_COL_WIDTH = 26;
    public static final int ICON_COL_WIDTH = 26;

    private final ClientSecurityContext securityContext;
    private final MyDataGrid<DocumentType> dataGrid;
    private final MultiSelectionModelImpl<DocumentType> selectionModel;
    private final DataGridSelectionEventManager<DocumentType> selectionEventManager;

    private final DocumentTypeCache documentTypeCache;

    private DocumentUserPermissionsReport currentPermissions;
    private UserRef relatedUser;
    private DocRef relatedDoc;
    private DocPermissionRestClient docPermissionClient;
    private ExplorerClient explorerClient;

    @Inject
    public DocumentCreatePermissionsListPresenter(final EventBus eventBus,
                                                  final DocumentCreatePermissionsListView view,
                                                  final DocumentTypeCache documentTypeCache,
                                                  final ClientSecurityContext securityContext) {
        super(eventBus, view);
        this.documentTypeCache = documentTypeCache;
        this.securityContext = securityContext;

        final boolean updatable = true;
        final Appearance appearance = updatable
                ? new TickBoxCell.DefaultAppearance()
                : new TickBoxCell.NoBorderAppearance();

        dataGrid = new MyDataGrid<>();
        selectionModel = new MultiSelectionModelImpl<>(dataGrid);
        selectionEventManager = new DataGridSelectionEventManager<>(dataGrid, selectionModel, false);
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
            if (GwtNullSafe.set(GwtNullSafe.get(currentPermissions,
                            DocumentUserPermissionsReport::getExplicitCreatePermissions))
                    .contains(documentType.getType())) {
                addDirect(sb, "Explicit Permission");
            }

            // See if implied by 'Create All' permission.
            if (GwtNullSafe.set(GwtNullSafe.get(currentPermissions,
                            DocumentUserPermissionsReport::getExplicitCreatePermissions))
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
                    GwtNullSafe.map(GwtNullSafe.get(currentPermissions,
                                    DocumentUserPermissionsReport::getInheritedCreatePermissionPaths))
                            .get(ExplorerConstants.ALL_CREATE_PERMISSIONS),
                    "Implied By 'Create All' Permission Inherited From:");

            // See if inherited.
            addPaths(
                    sb,
                    GwtNullSafe.map(GwtNullSafe.get(currentPermissions,
                                    DocumentUserPermissionsReport::getInheritedCreatePermissionPaths))
                            .get(documentType.getType()),
                    "Inherited From:");

            // See if inherited and implied by ownership.
            addPaths(
                    sb,
                    GwtNullSafe.map(GwtNullSafe.get(currentPermissions,
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
            if (currentPermissions.getExplicitCreatePermissions() != null &&
                    currentPermissions
                            .getExplicitCreatePermissions()
                            .contains(documentType.getType())) {
                return TickBoxState.TICK;
            } else if (currentPermissions.getExplicitCreatePermissions() != null &&
                    currentPermissions
                            .getExplicitCreatePermissions()
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
                    final boolean all = GwtNullSafe.set(GwtNullSafe.get(currentPermissions,
                                    DocumentUserPermissionsReport::getExplicitCreatePermissions))
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
                    onChangeAll(false);
                } else if (value.equals(TickBoxState.TICK)) {
                    onChangeAll(true);
                }
            });
            selectionColumn.setFieldUpdater((index, permission, value) -> {
                onChange(permission, TickBoxState.TICK.equals(value));
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
        documentTypeCache.fetch(documentTypes -> {
            refresh(documentTypes);
        }, this);
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

    public void setup(final UserRef relatedUser,
                      final DocRef relatedDoc,
                      final DocumentUserPermissionsReport permissions) {
        this.relatedUser = relatedUser;
        this.relatedDoc = relatedDoc;
        this.currentPermissions = permissions;

        refresh();
    }

    private void refreshAll() {
        docPermissionClient.getDocUserPermissionsReport(relatedDoc, relatedUser, response ->
                setup(relatedUser, relatedDoc, response));
    }

    private void onChangeAll(final boolean selected) {
        onChangeAll(selected, ok -> refreshAll());
    }

    private void onChangeAll(final boolean selected,
                             final Consumer<Boolean> consumer) {
        if (relatedUser != null) {
            final AbstractDocumentPermissionsChange change;
            if (selected) {
                change = new AbstractDocumentPermissionsChange
                        .AddAllDocumentCreatePermissions(relatedUser);
            } else {
                change = new AbstractDocumentPermissionsChange
                        .RemoveAllDocumentCreatePermissions(relatedUser);
            }

            final BulkDocumentPermissionChangeRequest request = new BulkDocumentPermissionChangeRequest(
                    createExpression(),
                    change);
            explorerClient.changeDocumentPermssions(request, response -> consumer.accept(response));
        } else {
            consumer.accept(false);
        }
    }

    private void onChange(final DocumentType documentType,
                          final boolean selected) {
        onChange(documentType, selected, ok -> refreshAll());
    }

    private void onChange(final DocumentType documentType,
                          final boolean selected,
                          final Consumer<Boolean> consumer) {
        // Get current value.
        final boolean currentSelectionState = currentPermissions != null &&
                currentPermissions.getExplicitCreatePermissions() != null &&
                currentPermissions.getExplicitCreatePermissions().contains(documentType.getType());

        if (selected != currentSelectionState && relatedUser != null) {
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

            final BulkDocumentPermissionChangeRequest request = new BulkDocumentPermissionChangeRequest(
                    createExpression(),
                    change);
            explorerClient.changeDocumentPermssions(request, response -> consumer.accept(response));
        } else {
            consumer.accept(false);
        }
    }

    private ExpressionOperator createExpression() {
        final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.OR);
        builder.addDocRefTerm(DocumentPermissionFields.DOCUMENT, Condition.IS_DOC_REF, relatedDoc);
        if (getView().isIncludeDescendants()) {
            builder.addTerm(ExpressionTerm.builder()
                    .field(DocumentPermissionFields.DESCENDANTS)
                    .condition(Condition.OF_DOC_REF)
                    .docRef(relatedDoc)
                    .build());
        }
        return builder.build();
    }

    public boolean isIncludeDescendants() {
        return getView().isIncludeDescendants();
    }

    private Preset getDocTypeIcon(final DocumentType documentType) {
        if (documentType != null) {
            return new Preset(documentType.getIcon(), documentType.getDisplayType(), true);
        } else {
            return null;
        }
    }

    public void setDocPermissionClient(final DocPermissionRestClient docPermissionClient) {
        this.docPermissionClient = docPermissionClient;
    }

    public void setExplorerClient(final ExplorerClient explorerClient) {
        this.explorerClient = explorerClient;
    }

    public interface DocumentCreatePermissionsListView extends View {

        void setTable(Widget widget);

        void setDetails(SafeHtml details);

        boolean isIncludeDescendants();
    }
}
