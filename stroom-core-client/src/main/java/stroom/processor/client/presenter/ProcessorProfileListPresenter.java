/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.processor.client.presenter;

import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.entity.shared.ExpressionCriteria;
import stroom.node.shared.FindNodeStatusCriteria;
import stroom.processor.shared.ProcessorProfile;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.ResultPage;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;

public class ProcessorProfileListPresenter extends MyPresenterWidget<PagerView> {

    private final ProcessorProfileClient processorProfileClient;
    private final MyDataGrid<ProcessorProfile> dataGrid;
    private final MultiSelectionModelImpl<ProcessorProfile> selectionModel;
    private final RestDataProvider<ProcessorProfile, ResultPage<ProcessorProfile>> dataProvider;

    @Inject
    public ProcessorProfileListPresenter(final EventBus eventBus,
                                         final PagerView view,
                                         final ProcessorProfileClient processorProfileClient) {
        super(eventBus, view);
        this.processorProfileClient = processorProfileClient;

        dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);
        getWidget().getElement().addClassName("default-min-sizes");

        initTableColumns();

        final ExpressionCriteria criteria = new ExpressionCriteria();
        dataProvider = new RestDataProvider<ProcessorProfile, ResultPage<ProcessorProfile>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<ProcessorProfile>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                CriteriaUtil.setRange(criteria, range);
                processorProfileClient.list(criteria, dataConsumer, errorHandler, view);
            }
        };
        dataProvider.addDataDisplay(dataGrid);
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Name.
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataGridUtil.toStringFunc(ProcessorProfile::getName))
                        .withSorting(FindNodeStatusCriteria.FIELD_ID_NAME)
                        .build(),
                DataGridUtil.headingBuilder("Name")
                        .withToolTip("The name of the processor profile.")
                        .build(),
                300);

        // Node Group.
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataGridUtil.toStringFunc(ProcessorProfile::getName))
                        .withSorting(FindNodeStatusCriteria.FIELD_ID_NAME)
                        .build(),
                DataGridUtil.headingBuilder("Node Group")
                        .withToolTip("The name of the node group.")
                        .build(),
                300);

        dataGrid.addEndColumn(new EndColumn<>());
    }

    private void internalRefresh() {
        dataProvider.refresh();
    }

    public MultiSelectionModel<ProcessorProfile> getSelectionModel() {
        return selectionModel;
    }

    public void refresh() {
        dataProvider.refresh();
    }
}
