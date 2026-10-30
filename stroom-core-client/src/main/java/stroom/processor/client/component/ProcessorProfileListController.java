/*
 * Copyright 2020 Crown Copyright
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

package stroom.processor.client.component;

import stroom.dispatch.client.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.processor.client.ProcessorProfileClient;
import stroom.processor.shared.FindProcessorProfileRequest;
import stroom.processor.shared.ProcessorProfile;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.widget.component.client.Controller;
import stroom.widget.datagrid.client.CriteriaUtil;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// The processor profiles ([ProcessorProfileList]), fetched a page at a time in the order chosen.
/// Its owner acts on New, Edit and Delete.
public class ProcessorProfileListController extends Controller {

    private final ProcessorProfileList view;
    private final RestDataProvider<ProcessorProfile, ResultPage<ProcessorProfile>> dataProvider;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus               The application's event bus.
    /// @param view                   The content.
    /// @param processorProfileClient Fetches the profiles.
    @Inject
    public ProcessorProfileListController(final EventBus eventBus,
                                          final ProcessorProfileList view,
                                          final ProcessorProfileClient processorProfileClient) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        final FindProcessorProfileRequest request = new FindProcessorProfileRequest(
                PageRequest.createDefault(),
                FindProcessorProfileRequest.DEFAULT_SORT_LIST,
                null);
        dataProvider = new RestDataProvider<>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<ProcessorProfile>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                CriteriaUtil.setRange(request, range);
                CriteriaUtil.setSortList(request, view.getSortList());
                processorProfileClient.find(request, dataConsumer, errorHandler, view.getTaskMonitor());
            }
        };
        view.setDataProvider(dataProvider);
        view.setOnSortChange(this::refresh);
    }

    /// Fetches the page shown again.
    public void refresh() {
        dataProvider.refresh();
    }

    /// @return The selection.
    public MultiSelectionModel<ProcessorProfile> getSelectionModel() {
        return view.getSelectionModel();
    }

    /// @param onNew Run when New is pressed.
    public void setOnNew(final Runnable onNew) {
        view.setOnNew(onNew);
    }

    /// @param onEdit Run when Edit is pressed, or a row is double clicked.
    public void setOnEdit(final Runnable onEdit) {
        view.setOnEdit(onEdit);
    }

    /// @param onDelete Run when Delete is pressed.
    public void setOnDelete(final Runnable onDelete) {
        view.setOnDelete(onDelete);
    }
}
