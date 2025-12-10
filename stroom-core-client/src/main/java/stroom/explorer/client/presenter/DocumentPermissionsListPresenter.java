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

package stroom.explorer.client.presenter;

import stroom.data.client.presenter.DocRefCell;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.explorer.client.event.FocusEvent;
import stroom.explorer.shared.AdvancedDocumentFindWithPermissionsRequest;
import stroom.explorer.shared.AdvancedDocumentFindWithPermissionsRequest.Builder;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.FindResult;
import stroom.explorer.shared.FindResultWithPermissions;
import stroom.query.api.ExpressionOperator;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentUserPermissions;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Objects;
import java.util.function.Consumer;

public class DocumentPermissionsListPresenter extends MyPresenterWidget<PagerView> {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    private final RestFactory restFactory;
    private final MyDataGrid<FindResultWithPermissions> dataGrid;
    private final MultiSelectionModelImpl<FindResultWithPermissions> selectionModel;
    private RestDataProvider<FindResultWithPermissions, ResultPage<FindResultWithPermissions>> dataProvider;
    private final Builder criteriaBuilder = new Builder();

    private ExpressionOperator lastFilter;
    private boolean focusText;
    private Consumer<ResultPage<FindResultWithPermissions>> currentResultHandler;

    @Inject
    public DocumentPermissionsListPresenter(final EventBus eventBus,
                                            final PagerView view,
                                            final RestFactory restFactory) {
        super(eventBus, view);
        this.restFactory = restFactory;

        dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        getView().setDataWidget(dataGrid);
        addColumns();
    }

    private void addColumns() {
        // Icon
//        final Column<FindResultWithPermissions, Preset> iconCol =
//                new Column<FindResultWithPermissions, Preset>(new SvgCell()) {
//                    @Override
//                    public Preset getValue(final FindResultWithPermissions row) {
//                        if (row != null && row.getFindResult() != null && row.getFindResult().getIcon() != null) {
//                            return new Preset(
//                                    row.getFindResult().getIcon(),
//                                    row.getFindResult().getDocRef().getType(),
//                                    true);
//                        }
//                        return null;
//                    }
//                };
//        iconCol.setSortable(true);
//        dataGrid.addColumn(iconCol, "</br>", ColumnSizeConstants.ICON_COL);

        // Display Name
        final DocRefCell.Builder<FindResultWithPermissions> cellBuilder =
                new DocRefCell.Builder<FindResultWithPermissions>()
                        .eventBus(getEventBus())
                        .docRefFunction(row2 -> NullSafe.get(
                                row2,
                                FindResultWithPermissions::getFindResult,
                                FindResult::getDocRef))
                        .showIcon(true);
        final Column<FindResultWithPermissions, FindResultWithPermissions> docNameCol =
                DataGridUtil.docRefColumnBuilder(cellBuilder).build();

        dataGrid.addResizableColumn(docNameCol, "Document", 400);

        // Permission
        final Column<FindResultWithPermissions, String> fullNameCol =
                new Column<FindResultWithPermissions, String>(new TextCell()) {
                    @Override
                    public String getValue(final FindResultWithPermissions row) {
                        return NullSafe.get(
                                row,
                                FindResultWithPermissions::getPermissions,
                                DocumentUserPermissions::getPermission,
                                DocumentPermission::getDisplayValue);
                    }
                };
        dataGrid.addResizableColumn(fullNameCol, "Permission", 150);

        // Inherited Permission
        final Column<FindResultWithPermissions, String> inheritedPermissionCol =
                new Column<FindResultWithPermissions, String>(new TextCell()) {
                    @Override
                    public String getValue(final FindResultWithPermissions row) {
                        return NullSafe.get(
                                row,
                                FindResultWithPermissions::getPermissions,
                                DocumentUserPermissions::getInheritedPermission,
                                DocumentPermission::getDisplayValue);
                    }
                };
        dataGrid.addResizableColumn(inheritedPermissionCol, "Inherited Permission", 150);

        // Path
        final Column<FindResultWithPermissions, String> createPermissionCol =
                new Column<FindResultWithPermissions, String>(new TextCell()) {
                    @Override
                    public String getValue(final FindResultWithPermissions row) {
                        return NullSafe.get(
                                row,
                                FindResultWithPermissions::getFindResult,
                                FindResult::getPath);
                    }
                };
        dataGrid.addAutoResizableColumn(createPermissionCol, "Path", 400);

//        // Create Permissions
//        final Column<FindResultWithPermissions, String> createPermissionCol =
//                new Column<FindResultWithPermissions, String>(new TextCell()) {
//                    @Override
//                    public String getValue(final FindResultWithPermissions row) {
//                        return NullSafe.get(
//                                row.getPermissions(),
//                                DocumentUserPermissions::getDocumentCreatePermissions,
//                                set -> set.stream().collect(Collectors.joining(", ")));
//                    }
//                };
//        dataGrid.addResizableColumn(createPermissionCol, "Create Permission", 400);
//
//        // Inherited Create Permissions
//        final Column<FindResultWithPermissions, String> inheritedCreatePermissionCol =
//                new Column<FindResultWithPermissions, String>(new TextCell()) {
//                    @Override
//                    public String getValue(final FindResultWithPermissions row) {
//                        return NullSafe.get(
//                                row.getPermissions(),
//                                DocumentUserPermissions::getInheritedDocumentCreatePermissions,
//                                set -> set.stream().collect(Collectors.joining(", ")));
//                    }
//                };
//        dataGrid.addResizableColumn(inheritedCreatePermissionCol, "Inherited Create Permission", 400);

        dataGrid.addEndColumn(new EndColumn<>());


//        final ColumnSortEvent.Handler columnSortHandler = event -> {
//            final List<CriteriaFieldSort> sortList = new ArrayList<>();
//            if (event != null) {
//                final ColumnSortList columnSortList = event.getColumnSortList();
//                if (columnSortList != null) {
//                    for (int i = 0; i < columnSortList.size(); i++) {
//                        final ColumnSortInfo columnSortInfo = columnSortList.get(i);
//                        final Column<?, ?> column = columnSortInfo.getColumn();
//                        final boolean isAscending = columnSortInfo.isAscending();
//
//                        if (column.equals(iconCol)) {
//                            sortList.add(new CriteriaFieldSort(
//                                    UserFields.IS_GROUP.getFldName(),
//                                    !isAscending,
//                                    true));
//                        } else if (column.equals(nameCol)) {
//                            sortList.add(new CriteriaFieldSort(
//                                    UserFields.DISPLAY_NAME.getFldName(),
//                                    !isAscending,
//                                    true));
//                            sortList.add(new CriteriaFieldSort(
//                                    UserFields.NAME.getFldName(),
//                                    !isAscending,
//                                    true));
//                        }
//                    }
//                }
//            }
//            builder.sortList(sortList);
//            refresh();
//        };
//        dataGrid.addColumnSortHandler(columnSortHandler);
//        dataGrid.getColumnSortList().push(nameCol);
//
//        builder.sortList(List.of(
//                new CriteriaFieldSort(
//                        UserFields.DISPLAY_NAME.getFldName(),
//                        false,
//                        true),
//                new CriteriaFieldSort(
//                        UserFields.NAME.getFldName(),
//                        false,
//                        true)));
    }

    private void resetFocus() {
        if (focusText) {
            focusText = false;
            FocusEvent.fire(dataGrid);
        }
    }

    public void refresh() {
        if (dataProvider == null) {
            dataProvider = new RestDataProvider<FindResultWithPermissions, ResultPage<FindResultWithPermissions>>(
                    getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<FindResultWithPermissions>> dataConsumer,
                                    final RestErrorHandler errorHandler) {

                    criteriaBuilder.pageRequest(new PageRequest(range.getStart(), range.getLength()));
                    final AdvancedDocumentFindWithPermissionsRequest request = criteriaBuilder.build();
                    final ExpressionOperator filter = request.getExpression();
                    final boolean isFilterChange = !Objects.equals(lastFilter, filter);
                    lastFilter = filter;

                    restFactory
                            .create(EXPLORER_RESOURCE)
                            .method(res -> res.advancedFindWithPermissions(request))
                            .onSuccess(resultPage -> {
                                if (currentResultHandler != null) {
                                    currentResultHandler.accept(resultPage);
                                }
                                dataConsumer.accept(resultPage);

                                if (isFilterChange) {
                                    if (!resultPage.isEmpty()) {
                                        selectionModel.setSelected(resultPage.getValues().get(0));
                                    } else {
                                        selectionModel.clear();
                                    }
                                }
                                resetFocus();
                            })
                            .onFailure(errorHandler)
                            .taskMonitorFactory(getView())
                            .exec();
                }
            };
            dataProvider.addDataDisplay(dataGrid);

        } else {
            dataProvider.refresh();
        }
    }

    public Builder getCriteriaBuilder() {
        return criteriaBuilder;
    }

    public MultiSelectionModel<FindResultWithPermissions> getSelectionModel() {
        return selectionModel;
    }

    public void setCurrentResultHandler(final Consumer<ResultPage<FindResultWithPermissions>> currentResultHandler) {
        this.currentResultHandler = currentResultHandler;
    }

    public void resetRange() {
        dataGrid.setVisibleRange(new Range(0, PageRequest.DEFAULT_PAGE_LENGTH));
    }
}
