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

package stroom.cache.client.presenter;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.cache.shared.CacheRow;
import stroom.cache.shared.FetchCacheRowAction;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.streamstore.client.presenter.ActionDataProvider;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.util.client.MultiSelectionModel;

public class CacheListPresenter extends MyPresenterWidget<DataGridView<CacheRow>> {
    private final FetchCacheRowAction action = new FetchCacheRowAction();
    private ActionDataProvider<CacheRow> dataProvider;

    @Inject
    public CacheListPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher,
                              final TooltipPresenter tooltipPresenter) {
        super(eventBus, new DataGridViewImpl<CacheRow>(true));

        getView().addColumn(new Column<CacheRow, String>(new TextCell()) {
            @Override
            public String getValue(final CacheRow row) {
                return row.getCacheName();
            }
        }, "Name");

        getView().addEndColumn(new EndColumn<CacheRow>());

        dataProvider = new ActionDataProvider<CacheRow>(dispatcher, action);
        dataProvider.addDataDisplay(getView().getDataDisplay());
    }

    public MultiSelectionModel<CacheRow> getSelectionModel() {
        return getView().getSelectionModel();
    }
}
