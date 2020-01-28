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

import com.google.gwt.cell.client.ButtonCell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.cache.shared.CacheResource;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.util.shared.ResultPage;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.util.client.MultiSelectionModel;

import java.util.List;
import java.util.function.Consumer;

public class CacheListPresenter extends MyPresenterWidget<DataGridView<String>> {
    private static final CacheResource CACHE_RESOURCE = GWT.create(CacheResource.class);

    private RestDataProvider<String, StringResultPage> dataProvider;

    @Inject
    public CacheListPresenter(final EventBus eventBus,
                              final RestFactory restFactory,
                              final TooltipPresenter tooltipPresenter) {
        super(eventBus, new DataGridViewImpl<>(true));

        // Name
        getView().addResizableColumn(new Column<String, String>(new TextCell()) {
            @Override
            public String getValue(final String row) {
                return row;
            }
        }, "Name", 400);

        // Clear.
        final Column<String, String> clearColumn = new Column<String, String>(new ButtonCell()) {
            @Override
            public String getValue(final String row) {
                return "Clear";
            }
        };
        clearColumn.setFieldUpdater((index, row, value) -> {
            final Rest<Boolean> rest = restFactory.create();
            rest.call(CACHE_RESOURCE).clear(row, null);
        });
        getView().addColumn(clearColumn, "</br>", 50);

        getView().addEndColumn(new EndColumn<>());

        dataProvider = new RestDataProvider<String, StringResultPage>(eventBus) {
            @Override
            protected void exec(final Consumer<StringResultPage> dataConsumer, final Consumer<Throwable> throwableConsumer) {
                final Rest<List<String>> rest = restFactory.create();
                rest
                        .onSuccess(list -> {
                            final StringResultPage stringResultPage = new StringResultPage();
                            stringResultPage.init(list);
                            dataConsumer.accept(stringResultPage);
                        })
                        .onFailure(throwableConsumer)
                        .call(CACHE_RESOURCE).list();
            }
        };
        dataProvider.addDataDisplay(getView().getDataDisplay());
    }

    public MultiSelectionModel<String> getSelectionModel() {
        return getView().getSelectionModel();
    }

    private static class StringResultPage extends ResultPage<String> {
    }
}
