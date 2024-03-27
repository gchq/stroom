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

import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.OrderByColumn;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.RestFactory;
import stroom.security.shared.FindUserNameCriteria;
import stroom.security.shared.UserNameResource;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserName;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.DataGrid;
import com.google.gwt.view.client.Range;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

public class UserNameDataProvider implements Refreshable {

    private static final UserNameResource USER_RESOURCE = GWT.create(UserNameResource.class);

    private final EventBus eventBus;
    private final RestFactory restFactory;
    private final DataGrid<UserName> dataGrid;
    private RestDataProvider<UserName, ResultPage<UserName>> dataProvider;
    private FindUserNameCriteria criteria = new FindUserNameCriteria();

    public UserNameDataProvider(final EventBus eventBus,
                                final RestFactory restFactory,
                                final DataGrid<UserName> dataGrid) {
        this.eventBus = eventBus;
        this.restFactory = restFactory;
        this.dataGrid = dataGrid;

        dataGrid.addColumnSortHandler(event -> {
            if (event.getColumn() instanceof OrderByColumn<?, ?>) {
                final OrderByColumn<?, ?> orderByColumn = (OrderByColumn<?, ?>) event.getColumn();
                criteria.setSort(orderByColumn.getField(), !event.isSortAscending(), orderByColumn.isIgnoreCase());
                dataProvider.refresh();
            }
        });
    }

    public void setCriteria(final FindUserNameCriteria criteria) {
        this.criteria = criteria;
        if (dataProvider == null) {
            this.dataProvider = new RestDataProvider<UserName, ResultPage<UserName>>(eventBus) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<UserName>> dataConsumer,
                                    final Consumer<Throwable> throwableConsumer) {
                    CriteriaUtil.setRange(criteria, range);
                    restFactory
                            .forResultPageOf(UserName.class)
                            .onSuccess(dataConsumer)
                            .onFailure(throwableConsumer)
                            .call(USER_RESOURCE)
                            .findAssociates(criteria);
                }

                // We override the default set data functionality to allow the
                // examination and modification of data prior to setting it in
                // the display.
                @Override
                protected void changeData(final ResultPage<UserName> data) {
                    final ResultPage<UserName> processedData = processData(data);
                    super.changeData(processedData);
                }
            };
            dataProvider.addDataDisplay(dataGrid);

        } else {
            dataProvider.refresh();
        }
    }

    /**
     * We override the default set data functionality to allow the examination
     * and modification of data prior to setting it in the display.
     */
    protected ResultPage<UserName> processData(final ResultPage<UserName> data) {
        return data;
    }

    @Override
    public void refresh() {
        if (dataProvider != null) {
            dataProvider.refresh();
        }
    }
}
