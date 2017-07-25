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

package stroom.entity.client.presenter;

import com.google.gwt.user.cellview.client.ColumnSortEvent;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.OrderByColumn;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.EntityServiceFindAction;
import stroom.entity.shared.ResultList;
import stroom.entity.shared.Sort.Direction;
import stroom.streamstore.client.presenter.ActionDataProvider;
import stroom.util.shared.SharedObject;

public class EntityServiceFindActionDataProvider<C extends BaseCriteria, E extends SharedObject>
        implements Refreshable, ColumnSortEvent.Handler {
    private final ClientDispatchAsync dispatcher;
    private final DataGridView<E> view;
    private EntityServiceFindAction<C, E> findAction;
    private ActionDataProvider<E> dataProvider;
    private Boolean allowNoConstraint = null;

    public EntityServiceFindActionDataProvider(final ClientDispatchAsync dispatcher, final DataGridView<E> view) {
        this.dispatcher = dispatcher;
        this.view = view;
        view.addColumnSortHandler(this);
    }

    public C getCriteria() {
        if (findAction != null) {
            return findAction.getCriteria();
        }
        return null;
    }

    public void setCriteria(final C criteria) {
        if (findAction == null) {
            findAction = new EntityServiceFindAction<C, E>(criteria);
        } else {
            findAction.setCriteria(criteria);
        }
        if (dataProvider == null) {
            this.dataProvider = new ActionDataProvider<E>(dispatcher, findAction) {
                // We override the default set data functionality to allow the
                // examination and modification of data prior to setting it in
                // the display.
                @Override
                protected void changeData(final ResultList<E> data) {
                    final ResultList<E> processedData = processData(data);
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
            dataProvider.addDataDisplay(view.getDataDisplay());

        } else {
            dataProvider.refresh();
        }
    }

    /**
     * We override the default set data functionality to allow the examination
     * and modification of data prior to setting it in the display.
     */
    protected ResultList<E> processData(final ResultList<E> data) {
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
            final OrderByColumn<?, ?> orderByColumn = (OrderByColumn<?, ?>) event.getColumn();
            if (findAction != null) {
                if (event.isSortAscending()) {
                    findAction.getCriteria().setSort(orderByColumn.getField(), Direction.ASCENDING, orderByColumn.isIgnoreCase());
                } else {
                    findAction.getCriteria().setSort(orderByColumn.getField(), Direction.DESCENDING, orderByColumn.isIgnoreCase());
                }
                refresh();
            }
        }
    }

    public ActionDataProvider<E> getDataProvider() {
        return dataProvider;
    }

    @Override
    public void refresh() {
        if (dataProvider != null) {
            dataProvider.refresh();
        }
    }
}
