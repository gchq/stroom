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

package stroom.index.client.presenter;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.entity.shared.ExpressionCriteria;
import stroom.index.shared.IndexVolumeGroup;
import stroom.index.shared.IndexVolumeGroupResource;
import stroom.util.client.BorderUtil;
import stroom.util.shared.ResultPage;
import stroom.widget.util.client.MultiSelectionModel;

import java.util.function.Consumer;

public class IndexVolumeGroupListPresenter extends MyPresenterWidget<DataGridView<IndexVolumeGroup>> {
    private static final IndexVolumeGroupResource INDEX_VOLUME_GROUP_RESOURCE = GWT.create(IndexVolumeGroupResource.class);

    private final RestDataProvider<IndexVolumeGroup, ResultPage<IndexVolumeGroup>> dataProvider;

    @Inject
    public IndexVolumeGroupListPresenter(final EventBus eventBus, final RestFactory restFactory) {
        super(eventBus, new DataGridViewImpl<>(true, true));

        // Add a border to the list.
        BorderUtil.addBorder(getWidget().getElement());

        initTableColumns();

        final ExpressionCriteria criteria = new ExpressionCriteria();
        dataProvider = new RestDataProvider<IndexVolumeGroup, ResultPage<IndexVolumeGroup>>(eventBus, criteria.obtainPageRequest()) {
            @Override
            protected void exec(final Consumer<ResultPage<IndexVolumeGroup>> dataConsumer, final Consumer<Throwable> throwableConsumer) {
                final Rest<ResultPage<IndexVolumeGroup>> rest = restFactory.create();
                rest.onSuccess(dataConsumer).onFailure(throwableConsumer).call(INDEX_VOLUME_GROUP_RESOURCE).find(criteria);
            }
        };
        dataProvider.addDataDisplay(getView().getDataDisplay());
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Name.
        final Column<IndexVolumeGroup, String> volumeColumn = new Column<IndexVolumeGroup, String>(new TextCell()) {
            @Override
            public String getValue(final IndexVolumeGroup volume) {
                return volume.getName();
            }
        };
        getView().addResizableColumn(volumeColumn, "Name", 400);

        getView().addEndColumn(new EndColumn<>());
    }

    public MultiSelectionModel<IndexVolumeGroup> getSelectionModel() {
        return getView().getSelectionModel();
    }

    public void refresh() {
        dataProvider.refresh();
    }
}
