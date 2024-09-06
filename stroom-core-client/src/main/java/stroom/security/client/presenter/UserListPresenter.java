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

import stroom.cell.info.client.SvgCell;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.PageRequestUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.QuickFilterExpressionParser;
import stroom.security.shared.User;
import stroom.security.shared.UserFields;
import stroom.security.shared.UserResource;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.ResultPage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.dropdowntree.client.view.QuickFilterPageView;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortEvent;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.cellview.client.ColumnSortList.ColumnSortInfo;
import com.google.gwt.user.cellview.client.DataGrid;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class UserListPresenter
        extends MyPresenterWidget<QuickFilterPageView>
        implements QuickFilterUiHandlers {

    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    private final PagerView pagerView;
    private final MyDataGrid<User> dataGrid;
    private final MultiSelectionModelImpl<User> selectionModel;
    private final RestFactory restFactory;
    private final FindUserCriteria.Builder builder = new FindUserCriteria.Builder();
    private RestDataProvider<User, ResultPage<User>> dataProvider;
    private User selected;
    private ExpressionTerm additionalTerm;
    private String filter;
    private ResultPage<User> currentData;
    private Consumer<ResultPage<User>> resultPageConsumer;

    @Inject
    public UserListPresenter(final EventBus eventBus,
                             final QuickFilterPageView userListView,
                             final PagerView pagerView,
                             final RestFactory restFactory,
                             final UiConfigCache uiConfigCache) {
        super(eventBus, userListView);
        this.pagerView = pagerView;
        this.restFactory = restFactory;

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

        // Icon
        final Column<User, Preset> iconCol = new Column<User, Preset>(new SvgCell()) {
            @Override
            public Preset getValue(final User user) {
                if (!user.isGroup()) {
                    return SvgPresets.USER;
                }

                return SvgPresets.USER_GROUP;
            }
        };
        iconCol.setSortable(true);
        dataGrid.addColumn(iconCol, "</br>", ColumnSizeConstants.ICON_COL);

        // Display Name
        final Column<User, String> nameCol = new Column<User, String>(new TextCell()) {
            @Override
            public String getValue(final User user) {
                if (user.getDisplayName() != null) {
                    if (!Objects.equals(user.getDisplayName(), user.getSubjectId())) {
                        return user.getDisplayName() + " (" + user.getSubjectId() + ")";
                    } else {
                        return user.getDisplayName();
                    }
                }
                return user.getSubjectId();
            }
        };
        nameCol.setSortable(true);
        dataGrid.addResizableColumn(nameCol, "Name", 400);

        // Full name
        final Column<User, String> fullNameCol = new Column<User, String>(new TextCell()) {
            @Override
            public String getValue(final User user) {
                return user.getFullName();
            }
        };
        dataGrid.addResizableColumn(fullNameCol, "Full Name", 400);
        dataGrid.addEndColumn(new EndColumn<>());


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
                                    UserFields.NAME.getFldName(),
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

        builder.sortList(List.of(
                new CriteriaFieldSort(
                        UserFields.DISPLAY_NAME.getFldName(),
                        false,
                        true),
                new CriteriaFieldSort(
                        UserFields.NAME.getFldName(),
                        false,
                        true)));
    }

    @Override
    public void onFilterChange(final String text) {
        filter = text;
        if (filter != null) {
            filter = filter.trim();
            if (filter.length() == 0) {
                filter = null;
            }
        }
        refresh();
    }

    public void refresh() {
        if (dataProvider == null) {
            this.dataProvider = new RestDataProvider<User, ResultPage<User>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<User>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    ExpressionOperator expression = QuickFilterExpressionParser
                            .parse(filter, UserFields.DEFAULT_FIELDS, UserFields.ALL_FIELD_MAP);
                    if (additionalTerm != null) {
                        expression = expression.copy().addTerm(additionalTerm).build();
                    }
                    builder.expression(expression);
                    builder.pageRequest(PageRequestUtil.createPageRequest(range));
                    restFactory
                            .create(USER_RESOURCE)
                            .method(res -> res.find(builder.build()))
                            .onSuccess(dataConsumer)
                            .onFailure(errorHandler)
                            .taskHandlerFactory(pagerView)
                            .exec();
                }

                @Override
                protected void changeData(final ResultPage<User> data) {
                    currentData = data;
                    super.changeData(data);
                    if (resultPageConsumer != null) {
                        resultPageConsumer.accept(data);
                    }
                }
            };
            dataProvider.addDataDisplay(dataGrid);

        } else {
            dataProvider.refresh();
        }
    }

    public MultiSelectionModelImpl<User> getSelectionModel() {
        return selectionModel;
    }

    public DataGrid<User> getDataGrid() {
        return dataGrid;
    }

    public ButtonView addButton(final Preset preset) {
        return pagerView.addButton(preset);
    }

    public void setAdditionalTerm(final ExpressionTerm additionalTerm) {
        this.additionalTerm = additionalTerm;
    }

    public PagerView getPagerView() {
        return pagerView;
    }

    public ResultPage<User> getCurrentData() {
        return currentData;
    }

    public void setResultPageConsumer(final Consumer<ResultPage<User>> resultPageConsumer) {
        this.resultPageConsumer = resultPageConsumer;
    }
}
