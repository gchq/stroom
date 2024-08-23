package stroom.explorer.client.presenter;

import stroom.cell.info.client.SvgCell;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.entity.shared.ExpressionCriteria;
import stroom.explorer.client.event.FocusEvent;
import stroom.explorer.shared.AdvancedDocumentFindWithPermissionsRequest;
import stroom.explorer.shared.AdvancedDocumentFindWithPermissionsRequest.Builder;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.FindResult;
import stroom.explorer.shared.FindResultWithPermissions;
import stroom.query.api.v2.ExpressionOperator;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentUserPermissions;
import stroom.svg.client.Preset;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
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
import java.util.Set;
import java.util.function.Consumer;

public class DocumentPermissionsListPresenter extends MyPresenterWidget<PagerView> {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    private final RestFactory restFactory;
    private final MyDataGrid<FindResultWithPermissions> dataGrid;
    private final MultiSelectionModelImpl<FindResultWithPermissions> selectionModel;
    private RestDataProvider<FindResultWithPermissions, ResultPage<FindResultWithPermissions>> dataProvider;
    private final Builder criteriaBuilder = new Builder();

    private ExpressionCriteria currentQuery = criteriaBuilder.build();
    private ExpressionOperator lastFilter;
    private boolean focusText;
    private Consumer<ResultPage<FindResultWithPermissions>> currentResulthandler;

    @Inject
    public DocumentPermissionsListPresenter(final EventBus eventBus,
                                            final PagerView view,
                                            final RestFactory restFactory) {
        super(eventBus, view);
        this.restFactory = restFactory;

        dataGrid = new MyDataGrid<>();
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        getView().setDataWidget(dataGrid);

        addColumns();
    }

    private void addColumns() {
        // Icon
        final Column<FindResultWithPermissions, Preset> iconCol =
                new Column<FindResultWithPermissions, Preset>(new SvgCell()) {
                    @Override
                    public Preset getValue(final FindResultWithPermissions row) {
                        if (row != null && row.getFindResult() != null && row.getFindResult().getIcon() != null) {
                            return new Preset(
                                    row.getFindResult().getIcon(),
                                    row.getFindResult().getDocRef().getType(),
                                    true);
                        }
                        return null;
                    }
                };
        iconCol.setSortable(true);
        dataGrid.addColumn(iconCol, "</br>", ColumnSizeConstants.ICON_COL);

        // Display Name
        final Column<FindResultWithPermissions, String> nameCol =
                new Column<FindResultWithPermissions, String>(new TextCell()) {
                    @Override
                    public String getValue(final FindResultWithPermissions row) {
                        return row.getFindResult().getDocRef().getDisplayValue();
                    }
                };
        nameCol.setSortable(true);
        dataGrid.addResizableColumn(nameCol, "Document Name", 400);

        // Permission
        final Column<FindResultWithPermissions, String> fullNameCol =
                new Column<FindResultWithPermissions, String>(new TextCell()) {
                    @Override
                    public String getValue(final FindResultWithPermissions row) {
                        return GwtNullSafe.get(
                                row.getPermissions(),
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
                        return GwtNullSafe.get(
                                row.getPermissions(),
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
                        return GwtNullSafe.get(
                                row.getFindResult(),
                                FindResult::getPath);
                    }
                };
        dataGrid.addAutoResizableColumn(createPermissionCol, "Path", 400);

//        // Create Permissions
//        final Column<FindResultWithPermissions, String> createPermissionCol =
//                new Column<FindResultWithPermissions, String>(new TextCell()) {
//                    @Override
//                    public String getValue(final FindResultWithPermissions row) {
//                        return GwtNullSafe.get(
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
//                        return GwtNullSafe.get(
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
                    final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
                    criteriaBuilder.pageRequest(pageRequest);

                    final AdvancedDocumentFindWithPermissionsRequest request = criteriaBuilder.build();
                    final ExpressionOperator filter = request.getExpression();
                    final boolean filterChange = !Objects.equals(lastFilter, filter);
                    lastFilter = filter;

                    restFactory
                            .create(EXPLORER_RESOURCE)
                            .method(res -> res.advancedFindWithPermissions(request))
                            .onSuccess(resultPage -> {
                                if (currentResulthandler != null) {
                                    currentResulthandler.accept(resultPage);
                                }
                                dataConsumer.accept(resultPage);

                                if (filterChange) {
                                    if (resultPage.size() > 0) {
                                        selectionModel.setSelected(resultPage.getValues().get(0));
                                    } else {
                                        selectionModel.clear();
                                    }
                                }

                                resetFocus();
                            })
                            .onFailure(errorHandler)
                            .taskListener(getView())
                            .exec();
                }
            };
            dataProvider.addDataDisplay(dataGrid);

        } else {
            dataProvider.refresh();
        }
    }


    public void setUserRef(final UserRef userRef) {
        criteriaBuilder.userRef(userRef);
    }

    public void setExpression(final ExpressionOperator expression) {
        criteriaBuilder.expression(expression);
    }

    public void setRequiredPermissions(final Set<DocumentPermission> requiredPermissions) {
        criteriaBuilder.requiredPermissions(requiredPermissions);
    }

    public MultiSelectionModel<FindResultWithPermissions> getSelectionModel() {
        return selectionModel;
    }

    public void setCurrentResulthandler(final Consumer<ResultPage<FindResultWithPermissions>> currentResulthandler) {
        this.currentResulthandler = currentResulthandler;
    }
}
