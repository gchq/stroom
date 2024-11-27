/*
 * Copyright 2017 Crown Copyright
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

import stroom.cell.info.client.SvgCell;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.PageRequestUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.DocumentTypeCache;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypes;
import stroom.query.api.v2.ExpressionOperator;
import stroom.security.shared.DocPermissionResource;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentUserPermissions;
import stroom.security.shared.FetchDocumentUserPermissionsRequest;
import stroom.security.shared.QuickFilterExpressionParser;
import stroom.security.shared.UserFields;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.ButtonView;
import stroom.widget.dropdowntree.client.view.QuickFilterDialogView;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortEvent;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.cellview.client.ColumnSortList.ColumnSortInfo;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class DocumentUserPermissionsListPresenter
        extends MyPresenterWidget<QuickFilterDialogView>
        implements QuickFilterUiHandlers {

    private static final DocPermissionResource DOC_PERMISSION_RESOURCE = GWT.create(DocPermissionResource.class);

    private final RestFactory restFactory;
    private final FetchDocumentUserPermissionsRequest.Builder builder =
            new FetchDocumentUserPermissionsRequest.Builder();
    private final MyDataGrid<DocumentUserPermissions> dataGrid;
    private final MultiSelectionModelImpl<DocumentUserPermissions> selectionModel;
    private final PagerView pagerView;
    private RestDataProvider<DocumentUserPermissions, ResultPage<DocumentUserPermissions>> dataProvider;
    private DocumentTypes documentTypes;
    private DocRef docRef;

    @Inject
    public DocumentUserPermissionsListPresenter(final EventBus eventBus,
                                                final QuickFilterDialogView userListView,
                                                final PagerView pagerView,
                                                final RestFactory restFactory,
                                                final UiConfigCache uiConfigCache,
                                                final DocumentTypeCache documentTypeCache) {
        super(eventBus, userListView);
        this.restFactory = restFactory;
        this.pagerView = pagerView;
        documentTypeCache.fetch(dt -> {
            this.documentTypes = dt;
            setupColumns(dt);
        }, this);

        dataGrid = new MyDataGrid<>();
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        pagerView.setDataWidget(dataGrid);

        // Not easy to determine if we are dealing in users or groups at this point so just
        // call it Quick Filter
        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                userListView.registerPopupTextProvider(() -> QuickFilterTooltipUtil.createTooltip(
                        "Quick Filter",
                        UserFields.FILTER_FIELD_DEFINITIONS,
                        uiConfig.getHelpUrlQuickFilter()));
            }
        }, this);

        userListView.setDataView(pagerView);
        userListView.setUiHandlers(this);

        builder.sortList(List.of(
                new CriteriaFieldSort(
                        UserFields.DISPLAY_NAME.getFldName(),
                        false,
                        true),
                new CriteriaFieldSort(
                        UserFields.UNIQUE_ID.getFldName(),
                        false,
                        true)));
    }

    @Override
    public void onFilterChange(String text) {
        if (text != null) {
            text = text.trim();
            if (text.isEmpty()) {
                text = null;
            }
        }

        final ExpressionOperator expression = QuickFilterExpressionParser
                .parse(text, UserFields.DEFAULT_FIELDS, UserFields.ALL_FIELDS_MAP);
        builder.expression(expression);
        refresh();
    }

    public void refresh() {
        if (dataProvider == null) {
            dataProvider =
                    new RestDataProvider<DocumentUserPermissions, ResultPage<DocumentUserPermissions>>(
                            getEventBus()) {
                        @Override
                        protected void exec(final Range range,
                                            final Consumer<ResultPage<DocumentUserPermissions>> dataConsumer,
                                            final RestErrorHandler errorHandler) {
                            builder.pageRequest(PageRequestUtil.createPageRequest(range));
                            final FetchDocumentUserPermissionsRequest request = builder.build();
                            restFactory
                                    .create(DOC_PERMISSION_RESOURCE)
                                    .method(res -> res.fetchDocumentUserPermissions(request))
                                    .onSuccess(dataConsumer)
                                    .onFailure(errorHandler)
                                    .taskMonitorFactory(pagerView)
                                    .exec();
                        }
                    };
            dataProvider.addDataDisplay(dataGrid);
        } else {
            dataProvider.refresh();
        }
    }

    public void setDocRef(final DocRef docRef) {
        this.docRef = docRef;
        builder.docRef(docRef);
    }


    public void setAllUsers(final boolean allUsers) {
        builder.allUsers(allUsers);
    }

    private void setupColumns(final DocumentTypes documentTypes) {
        // Icon
        final Column<DocumentUserPermissions, Preset> iconCol =
                new Column<DocumentUserPermissions, Preset>(new SvgCell()) {
                    @Override
                    public Preset getValue(final DocumentUserPermissions documentUserPermissions) {
                        final UserRef userRef = documentUserPermissions.getUserRef();
                        if (!userRef.isGroup()) {
                            return SvgPresets.USER;
                        }

                        return SvgPresets.USER_GROUP;
                    }
                };
        iconCol.setSortable(true);
        dataGrid.addColumn(iconCol, "</br>", ColumnSizeConstants.ICON_COL);

        // User Or Group Name
        final Column<DocumentUserPermissions, String> nameCol =
                new Column<DocumentUserPermissions, String>(new TextCell()) {
                    @Override
                    public String getValue(final DocumentUserPermissions documentUserPermissions) {
                        return documentUserPermissions.getUserRef().getDisplayName();
                    }
                };
        nameCol.setSortable(true);
        dataGrid.addResizableColumn(nameCol, "Display Name", 400);

        // Permission
        final Column<DocumentUserPermissions, SafeHtml> permissionCol =
                new Column<DocumentUserPermissions, SafeHtml>(new SafeHtmlCell()) {
                    @Override
                    public SafeHtml getValue(final DocumentUserPermissions documentUserPermissions) {
                        final DescriptionBuilder sb = new DescriptionBuilder();
                        final DocumentPermission explicit = documentUserPermissions.getPermission();
                        final DocumentPermission inherited = documentUserPermissions.getInheritedPermission();
                        if (inherited != null) {
                            if (explicit != null && explicit.isHigher(inherited)) {
                                sb.addLine(explicit.getDisplayValue());
                            } else {
                                sb.addLine(false, true, inherited.getDisplayValue());
                            }
                        } else if (explicit != null) {
                            sb.addLine(explicit.getDisplayValue());
                        }
                        return sb.toSafeHtml();
                    }
                };
        dataGrid.addResizableColumn(permissionCol, "Permission", 100);

        if (DocumentTypes.isFolder(docRef)) {
            // Document Create Permissions.
            final Column<DocumentUserPermissions, SafeHtml> documentCreateTypeCol =
                    new Column<DocumentUserPermissions, SafeHtml>(new SafeHtmlCell()) {
                        @Override
                        public SafeHtml getValue(final DocumentUserPermissions documentUserPermissions) {
                            final DescriptionBuilder sb = new DescriptionBuilder();
                            final Set<String> explicit = documentUserPermissions
                                    .getDocumentCreatePermissions();
                            final Set<String> inherited = documentUserPermissions
                                    .getInheritedDocumentCreatePermissions();
                            if ((explicit != null && explicit.size() > 0) ||
                                (inherited != null && inherited.size() > 0)) {

                                if (explicit != null && explicit.size() == documentTypes.getTypes().size()) {
                                    sb.addLine(true, false, "All");
                                } else if (inherited != null && inherited.size() == documentTypes.getTypes().size()) {
                                    sb.addLine(true, true, "All");
                                } else {
                                    boolean notEmpty = false;
                                    boolean lastInherited = false;
                                    final List<DocumentType> types = documentTypes
                                            .getTypes();
                                    types.sort(Comparator.comparing(DocumentType::getDisplayType));
                                    for (final DocumentType documentType : types) {
                                        if (explicit != null && explicit.contains(documentType.getType())) {
                                            if (notEmpty) {
                                                sb.addLine(false, lastInherited, ", ");
                                            }
                                            sb.addLine(documentType.getDisplayType());
                                            lastInherited = false;
                                            notEmpty = true;
                                        } else if (inherited != null &&
                                                   inherited.contains(documentType.getType())) {
                                            if (notEmpty) {
                                                sb.addLine(false, lastInherited, ", ");
                                            }
                                            sb.addLine(false, true, documentType.getDisplayType());
                                            lastInherited = true;
                                            notEmpty = true;
                                        }
                                    }
                                }
                            }

                            return sb.toSafeHtml();
                        }
                    };
            dataGrid.addAutoResizableColumn(documentCreateTypeCol, "Create Document Types", 100);
        }

        dataGrid.addEndColumn(new EndColumn<DocumentUserPermissions>());


        final ColumnSortEvent.Handler columnSortHandler = event -> {
            final List<CriteriaFieldSort> sortList = new ArrayList<>();
            if (event != null) {
                final ColumnSortList columnSortList = event.getColumnSortList();
                if (columnSortList != null) {
                    for (int i = 0; i < columnSortList.size(); i++) {
                        final ColumnSortInfo columnSortInfo = columnSortList.get(i);
                        final Column<?, ?> column = columnSortInfo.getColumn();
                        final boolean isAscending = columnSortInfo.isAscending();

                        if (column.equals(iconCol)) {
                            sortList.add(new CriteriaFieldSort(
                                    UserFields.IS_GROUP.getFldName(),
                                    !isAscending,
                                    true));
                        } else if (column.equals(nameCol)) {
                            sortList.add(new CriteriaFieldSort(
                                    UserFields.DISPLAY_NAME.getFldName(),
                                    !isAscending,
                                    true));
                            sortList.add(new CriteriaFieldSort(
                                    UserFields.UNIQUE_ID.getFldName(),
                                    !isAscending,
                                    true));
                        }
                    }
                }
            }
            builder.sortList(sortList);
            refresh();
        };
        dataGrid.addColumnSortHandler(columnSortHandler);
        dataGrid.getColumnSortList().push(nameCol);
    }

    public ButtonView addButton(final Preset preset) {
        return pagerView.addButton(preset);
    }

    public void addButton(final ButtonView buttonView) {
        pagerView.addButton(buttonView);
    }

    public MultiSelectionModel<DocumentUserPermissions> getSelectionModel() {
        return selectionModel;
    }

    public PagerView getPagerView() {
        return pagerView;
    }
}
