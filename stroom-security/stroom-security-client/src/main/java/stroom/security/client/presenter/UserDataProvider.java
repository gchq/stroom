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

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.ColumnSortEvent;
import com.google.web.bindery.event.shared.EventBus;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.OrderByColumn;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.User;
import stroom.security.shared.UserResource;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Sort.Direction;

import java.util.function.Consumer;

public class UserDataProvider implements Refreshable, ColumnSortEvent.Handler {
    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    private final EventBus eventBus;
    private final RestFactory restFactory;
    private final DataGridView<User> view;
    private RestDataProvider<User, ResultPage<User>> dataProvider;
    //    private Boolean allowNoConstraint = null;
    private FindUserCriteria criteria = new FindUserCriteria();

    public UserDataProvider(final EventBus eventBus, final RestFactory restFactory, final DataGridView<User> view) {
        this.eventBus = eventBus;
        this.restFactory = restFactory;
        this.view = view;
        view.addColumnSortHandler(this);
    }

//    public FindUserCriteria getCriteria() {
//        if (findAction != null) {
//            return findAction.getCriteria();
//        }
//        return null;
//    }

    public void setCriteria(final FindUserCriteria criteria) {
        this.criteria = criteria;
        if (dataProvider == null) {
            this.dataProvider = new RestDataProvider<User, ResultPage<User>>(eventBus, criteria.obtainPageRequest()) {
                @Override
                protected void exec(final Consumer<ResultPage<User>> dataConsumer, final Consumer<Throwable> throwableConsumer) {
                    final Rest<ResultPage<User>> rest = restFactory.create();
                    rest.onSuccess(dataConsumer).onFailure(throwableConsumer).call(USER_RESOURCE).find(criteria);
                }

                // We override the default set data functionality to allow the
                // examination and modification of data prior to setting it in
                // the display.
                @Override
                protected void changeData(final ResultPage<User> data) {
                    final ResultPage<User> processedData = processData(data);
                    super.changeData(processedData);
                }
            };
//            if (allowNoConstraint != null) {
//                dataProvider.setAllowNoConstraint(allowNoConstraint);
//            }
            // for (ChangeDataHandler<ResultList<E>> changeDataHandler :
            // pendingChangeHandlers) {
            // dataProvider.addChangeDataHandler(changeDataHandler);
            // }
            // pendingChangeHandlers.clear();
            dataProvider.addDataDisplay(view.getDataDisplay());

        } else {
            dataProvider.refresh();
        }
    }

    /**
     * We override the default set data functionality to allow the examination
     * and modification of data prior to setting it in the display.
     */
    protected ResultPage<User> processData(final ResultPage<User> data) {
        return data;
    }

//    public void setAllowNoConstraint(final boolean allowNoConstraint) {
//        this.allowNoConstraint = allowNoConstraint;
//        if (dataProvider != null) {
//            dataProvider.setAllowNoConstraint(allowNoConstraint);
//        }
//    }

    @Override
    public void onColumnSort(final ColumnSortEvent event) {
        if (event.getColumn() instanceof OrderByColumn<?, ?>) {
            final OrderByColumn<?, ?> orderByColumn = (OrderByColumn<?, ?>) event.getColumn();
            if (criteria != null) {
                if (event.isSortAscending()) {
                    criteria.setSort(orderByColumn.getField(), Direction.ASCENDING, orderByColumn.isIgnoreCase());
                } else {
                    criteria.setSort(orderByColumn.getField(), Direction.DESCENDING, orderByColumn.isIgnoreCase());
                }
                refresh();
            }
        }
    }

//    public ActionDataProvider<User> getDataProvider() {
//        return dataProvider;
//    }

    @Override
    public void refresh() {
        if (dataProvider != null) {
            dataProvider.refresh();
        }
    }
}
