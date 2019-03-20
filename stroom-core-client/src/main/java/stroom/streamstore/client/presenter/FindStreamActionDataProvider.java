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

package stroom.streamstore.client.presenter;

import com.google.gwt.user.cellview.client.ColumnSortEvent;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.OrderByColumn;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.util.shared.ResultList;
import stroom.util.shared.Sort.Direction;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.FindMetaRowAction;
import stroom.meta.shared.MetaRow;

public class FindStreamActionDataProvider implements Refreshable, ColumnSortEvent.Handler {
    private final ClientDispatchAsync dispatcher;
    private final DataGridView<MetaRow> view;
    private FindMetaRowAction findAction;
    private ActionDataProvider<MetaRow> dataProvider;
    private Boolean allowNoConstraint = null;

    public FindStreamActionDataProvider(final ClientDispatchAsync dispatcher, final DataGridView<MetaRow> view) {
        this.dispatcher = dispatcher;
        this.view = view;
        view.addColumnSortHandler(this);
    }

    public FindMetaCriteria getCriteria() {
        if (findAction != null) {
            return findAction.getCriteria();
        }
        return null;
    }

    public void setCriteria(final FindMetaCriteria criteria) {
        if (findAction == null) {
            findAction = new FindMetaRowAction(criteria);
        } else {
            findAction.setCriteria(criteria);
        }
        if (dataProvider == null) {
            this.dataProvider = new ActionDataProvider<MetaRow>(dispatcher, findAction) {
                // We override the default set data functionality to allow the
                // examination and modification of data prior to setting it in
                // the display.
                @Override
                protected void changeData(final ResultList<MetaRow> data) {
                    final ResultList<MetaRow> processedData = processData(data);
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
    protected ResultList<MetaRow> processData(final ResultList<MetaRow> data) {
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

    public ActionDataProvider<MetaRow> getDataProvider() {
        return dataProvider;
    }

    @Override
    public void refresh() {
        if (dataProvider != null) {
            dataProvider.refresh();
        }
    }
}
