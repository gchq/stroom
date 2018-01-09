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

package stroom.importexport.client.presenter;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.ColumnSortEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.OrderByColumn;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.Sort.Direction;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.importexport.shared.FetchDependenciesAction;
import stroom.query.api.v2.DocRef;
import stroom.streamstore.client.presenter.ActionDataProvider;
import stroom.svg.client.Icon;
import stroom.svg.client.SvgPresets;

public class DependenciesPresenter extends ContentTabPresenter<DataGridView<Dependency>> implements ColumnSortEvent.Handler {
    private final DependencyCriteria criteria;
    private final ActionDataProvider<Dependency> dataProvider;

    @Inject
    public DependenciesPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher) {
        super(eventBus, new DataGridViewImpl<>(false, 100));

        getView().addResizableColumn(new OrderByColumn<Dependency, String>(new TextCell(), DependencyCriteria.FIELD_FROM, true) {
            @Override
            public String getValue(final Dependency row) {
                return docRefToString(row.getFrom());
            }
        }, DependencyCriteria.FIELD_FROM, 500);

        getView().addResizableColumn(new OrderByColumn<Dependency, String>(new TextCell(), DependencyCriteria.FIELD_TO, true) {
            @Override
            public String getValue(final Dependency row) {
                return docRefToString(row.getTo());
            }
        }, DependencyCriteria.FIELD_TO, 500);

        getView().addResizableColumn(new OrderByColumn<Dependency, String>(new TextCell(), DependencyCriteria.FIELD_STATUS, false) {
            @Override
            public String getValue(final Dependency row) {
                if (row.isOk()) {
                    return "OK";
                }
                return "Missing";
            }
        }, DependencyCriteria.FIELD_STATUS, 100);

        getView().addEndColumn(new EndColumn<>());

        getView().addColumnSortHandler(this);

        criteria = new DependencyCriteria();
        final FetchDependenciesAction action = new FetchDependenciesAction(criteria);
        dataProvider = new ActionDataProvider<>(dispatcher, action);
        dataProvider.addDataDisplay(getView().getDataDisplay());

        dataProvider.refresh();
    }

    private String docRefToString(final DocRef docRef) {
        return docRef.getType() + ": " + docRef.getName() + " {" + docRef.getUuid() + "}";
    }

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
                dataProvider.refresh();
            }
        }
    }

    @Override
    public Icon getIcon() {
        return SvgPresets.DEPENDENCIES;
    }

    @Override
    public String getLabel() {
        return "Dependencies";
    }
}
