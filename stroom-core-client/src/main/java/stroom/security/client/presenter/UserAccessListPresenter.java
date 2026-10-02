/*
 * Copyright 2026 Crown Copyright
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

import stroom.alert.client.event.AlertEvent;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.security.shared.FindUserAccessCriteria;
import stroom.security.shared.UserAccessResource;
import stroom.security.shared.UserAccessRow;
import stroom.svg.client.Preset;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.dropdowntree.client.view.QuickFilterPageView;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Collections;
import java.util.function.Consumer;

/**
 * The grid of who currently holds access: one row per user, showing their sessions and the tokens the internal
 * IdP has issued them side by side.
 */
public class UserAccessListPresenter
        extends MyPresenterWidget<QuickFilterPageView>
        implements QuickFilterUiHandlers {

    private static final UserAccessResource USER_ACCESS_RESOURCE = GWT.create(UserAccessResource.class);

    private final RestFactory restFactory;
    private final DateTimeFormatter dateTimeFormatter;
    private final PagerView pagerView;
    private final MyDataGrid<UserAccessRow> dataGrid;
    private final MultiSelectionModelImpl<UserAccessRow> selectionModel;

    private RestDataProvider<UserAccessRow, ResultPage<UserAccessRow>> dataProvider = null;
    private String filter;

    @Inject
    public UserAccessListPresenter(final EventBus eventBus,
                                   final PagerView pagerView,
                                   final QuickFilterPageView listView,
                                   final RestFactory restFactory,
                                   final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, listView);
        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;
        this.pagerView = pagerView;
        this.dataGrid = new MyDataGrid<>(this);
        this.dataGrid.setTableName("User Access");
        this.selectionModel = dataGrid.addDefaultSelectionModel(true);
        pagerView.setDataWidget(dataGrid);

        listView.setDataView(pagerView);
        listView.setUiHandlers(this);
    }

    public MultiSelectionModelImpl<UserAccessRow> getSelectionModel() {
        return selectionModel;
    }

    private void initTableColumns() {
        // User. Falls back to the subject id server side, so this is never blank even for a subject that has
        // no stroom user - which is exactly the kind of row an administrator needs to be able to see.
        final Column<UserAccessRow, String> userColumn = DataGridUtil.textColumnBuilder(
                        UserAccessRow::getDisplayName)
                .build();
        dataGrid.addResizableColumn(userColumn, "User", 250);

        // Subject id, so a row whose display name is ambiguous can still be told apart, and so the id an
        // administrator sees in a log line can be matched to a row here.
        final Column<UserAccessRow, String> subjectColumn = DataGridUtil.textColumnBuilder(
                        UserAccessRow::getSubjectId)
                .build();
        dataGrid.addResizableColumn(subjectColumn, "Subject Id", 250);

        final Column<UserAccessRow, String> sessionsColumn = DataGridUtil.textColumnBuilder(
                        (UserAccessRow row) -> String.valueOf(row.getSessionCount()))
                .rightAligned()
                .build();
        dataGrid.addColumn(sessionsColumn, "Sessions", ColumnSizeConstants.SMALL_COL);

        final Column<UserAccessRow, String> nodesColumn = DataGridUtil.textColumnBuilder(
                        (UserAccessRow row) -> String.join(", ", NullSafe.list(row.getNodeNames())))
                .build();
        dataGrid.addResizableColumn(nodesColumn, "Nodes", 180);

        final Column<UserAccessRow, String> lastAccessedColumn = DataGridUtil.textColumnBuilder(
                        (UserAccessRow row) -> row.getLastAccessedMs() == null
                                ? ""
                                : dateTimeFormatter.formatWithDuration(row.getLastAccessedMs()))
                .build();
        dataGrid.addColumn(lastAccessedColumn, "Last Accessed", ColumnSizeConstants.DATE_AND_DURATION_COL);

        // Always zero against an external IdP, since nothing is minted here.
        final Column<UserAccessRow, String> tokensColumn = DataGridUtil.textColumnBuilder(
                        (UserAccessRow row) -> String.valueOf(row.getTokenCount()))
                .rightAligned()
                .build();
        dataGrid.addColumn(tokensColumn, "Tokens", ColumnSizeConstants.SMALL_COL);

        // How long token-based access would persist if left alone - usually driven by a refresh token, so
        // weeks rather than the access token's hour. This is the number that makes the case for revoking.
        final Column<UserAccessRow, String> tokenExpiryColumn = DataGridUtil.textColumnBuilder(
                        (UserAccessRow row) -> row.getLastTokenExpiryMs() == null
                                ? ""
                                : dateTimeFormatter.formatWithDuration(row.getLastTokenExpiryMs()))
                .build();
        dataGrid.addAutoResizableColumn(
                tokenExpiryColumn, "Access Expires", ColumnSizeConstants.DATE_AND_DURATION_COL);
    }

    private void fetchData(final Range range,
                           final Consumer<ResultPage<UserAccessRow>> dataConsumer,
                           final RestErrorHandler errorHandler,
                           final TaskMonitorFactory taskMonitorFactory) {
        final FindUserAccessCriteria criteria = new FindUserAccessCriteria();
        criteria.setFilter(filter);
        criteria.setPageRequest(CriteriaUtil.createPageRequest(range));
        criteria.setSortList(CriteriaUtil.createSortList(dataGrid.getColumnSortList()));

        restFactory
                .create(USER_ACCESS_RESOURCE)
                .method(res -> res.find(criteria))
                .onSuccess(dataConsumer)
                .onFailure(throwable -> AlertEvent.fireError(
                        this,
                        "Error fetching user access: " + throwable.getMessage(),
                        null))
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void clear() {
        selectionModel.clear();
        if (dataProvider != null) {
            dataProvider.getDataDisplays().forEach(hasData -> {
                hasData.setRowData(0, Collections.emptyList());
                hasData.setRowCount(0, true);
            });
        }
    }

    public void refresh() {
        if (dataProvider == null) {
            initTableColumns();
            //noinspection Convert2Diamond // Cos GWT
            dataProvider = new RestDataProvider<UserAccessRow, ResultPage<UserAccessRow>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<UserAccessRow>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    fetchData(range, dataConsumer, errorHandler, pagerView);
                }

                @Override
                protected void changeData(final ResultPage<UserAccessRow> data) {
                    super.changeData(data);
                    if (!data.isEmpty()) {
                        selectionModel.setSelected(data.getFirst());
                    } else {
                        selectionModel.clear();
                    }
                }
            };
            dataProvider.addDataDisplay(dataGrid);
        } else {
            dataProvider.refresh();
        }
    }

    public ButtonView addButton(final Preset preset) {
        return pagerView.addButton(preset);
    }

    public void setQuickFilter(final String userInput) {
        clear();
        getView().setQuickFilterText(userInput);
        onFilterChange(userInput);
    }

    @Override
    public void onFilterChange(final String text) {
        filter = NullSafe.trim(text);
        if (filter.isEmpty()) {
            filter = null;
        }
        refresh();
    }
}
