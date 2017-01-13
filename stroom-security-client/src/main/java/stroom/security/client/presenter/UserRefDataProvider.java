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

import com.google.gwt.user.cellview.client.ColumnSortEvent;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.OrderByColumn;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.BaseCriteria.OrderByDirection;
import stroom.entity.shared.OrderBy;
import stroom.entity.shared.ResultList;
import stroom.security.shared.FetchUserRefAction;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.UserRef;
import stroom.streamstore.client.presenter.ActionDataProvider;

public class UserRefDataProvider
        implements Refreshable, ColumnSortEvent.Handler {
    private final ClientDispatchAsync dispatcher;
    private final DataGridView<UserRef> view;
    private FetchUserRefAction findAction;
    private ActionDataProvider<UserRef> dataProvider;
    private Boolean allowNoConstraint = null;

    public UserRefDataProvider(final ClientDispatchAsync dispatcher, final DataGridView<UserRef> view) {
        this.dispatcher = dispatcher;
        this.view = view;
        view.addColumnSortHandler(this);
    }

    public FindUserCriteria getCriteria() {
        if (findAction != null) {
            return findAction.getCriteria();
        }
        return null;
    }

    public void setCriteria(final FindUserCriteria criteria) {
        if (findAction == null) {
            findAction = new FetchUserRefAction(criteria);
        } else {
            findAction.setCriteria(criteria);
        }
        if (dataProvider == null) {
            this.dataProvider = new ActionDataProvider<UserRef>(dispatcher, findAction) {
                // We override the default set data functionality to allow the
                // examination and modification of data prior to setting it in
                // the display.
                @Override
                protected void changeData(final ResultList<UserRef> data) {
                    final ResultList<UserRef> processedData = processData(data);
                    super.changeData(processedData);
                }
            };
            if (allowNoConstraint != null) {
                dataProvider.setAllowNoConstraint(allowNoConstraint);
            }
            // for (ChangeDataHandler<ResultList<E>> changeDataHandler :
            // pendingChangeHandlers) {
            // dataProvider.addChangeDataHandler(changeDataHandler);
            // }
            // pendingChangeHandlers.clear();
            dataProvider.addDataDisplay(view);

        } else {
            dataProvider.refresh();
        }
    }

    /**
     * We override the default set data functionality to allow the examination
     * and modification of data prior to setting it in the display.
     */
    protected ResultList<UserRef> processData(final ResultList<UserRef> data) {
        return data;
    }

    public void setAllowNoConstraint(final boolean allowNoConstraint) {
        this.allowNoConstraint = allowNoConstraint;
        if (dataProvider != null) {
            dataProvider.setAllowNoConstraint(allowNoConstraint);
        }
    }

    @Override
    public void onColumnSort(final ColumnSortEvent event) {
        if (event.getColumn() instanceof OrderByColumn<?, ?>) {
            final OrderBy orderBy = ((OrderByColumn<?, ?>) event.getColumn()).getOrderBy();
            if (findAction != null) {
                if (event.isSortAscending()) {
                    findAction.getCriteria().setOrderBy(orderBy, OrderByDirection.ASCENDING);
                } else {
                    findAction.getCriteria().setOrderBy(orderBy, OrderByDirection.DESCENDING);
                }
                refresh();
            }
        }
    }

    public ActionDataProvider<UserRef> getDataProvider() {
        return dataProvider;
    }

    @Override
    public void refresh() {
        if (dataProvider != null) {
            dataProvider.refresh();
        }
    }
}
