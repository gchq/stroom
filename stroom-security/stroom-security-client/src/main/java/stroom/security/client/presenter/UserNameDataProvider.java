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

import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.OrderByColumn;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.security.shared.FindUserNameCriteria;
import stroom.security.shared.UserNameResource;
import stroom.util.shared.ResultPage;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.DataGrid;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

public class UserNameDataProvider implements Refreshable {

    private static final UserNameResource USER_RESOURCE = GWT.create(UserNameResource.class);

    private final EventBus eventBus;
    private final RestFactory restFactory;
    private final DataGrid<String> dataGrid;
    private RestDataProvider<String, ResultPage<String>> dataProvider;
    private FindUserNameCriteria criteria = new FindUserNameCriteria();

    public UserNameDataProvider(final EventBus eventBus,
                                final RestFactory restFactory,
                                final DataGrid<String> dataGrid) {
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
            this.dataProvider = new RestDataProvider<String, ResultPage<String>>(eventBus,
                    criteria.obtainPageRequest()) {
                @Override
                protected void exec(final Consumer<ResultPage<String>> dataConsumer,
                                    final Consumer<Throwable> throwableConsumer) {
                    final Rest<ResultPage<String>> rest = restFactory.create();
                    rest
                            .onSuccess(dataConsumer)
                            .onFailure(throwableConsumer)
                            .call(USER_RESOURCE)
                            .find(criteria);
                }

                // We override the default set data functionality to allow the
                // examination and modification of data prior to setting it in
                // the display.
                @Override
                protected void changeData(final ResultPage<String> data) {
                    final ResultPage<String> processedData = processData(data);
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
    protected ResultPage<String> processData(final ResultPage<String> data) {
        return data;
    }

    @Override
    public void refresh() {
        if (dataProvider != null) {
            dataProvider.refresh();
        }
    }
}
