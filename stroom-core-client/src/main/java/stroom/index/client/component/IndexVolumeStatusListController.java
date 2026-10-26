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

package stroom.index.client.component;

import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.entity.shared.ExpressionCriteria;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeResource;
import stroom.preferences.client.DateTimeFormatter;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.ResultPage;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// The index volumes its owner's criteria find ([IndexVolumeStatusList]), a page at a time.
public class IndexVolumeStatusListController extends Controller {

    private static final IndexVolumeResource INDEX_VOLUME_RESOURCE = GWT.create(IndexVolumeResource.class);

    private final IndexVolumeStatusList view;
    private final RestFactory restFactory;
    private Consumer<ResultPage<IndexVolume>> consumer;
    private RestDataProvider<IndexVolume, ResultPage<IndexVolume>> dataProvider;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus          The application's event bus.
    /// @param view              The content.
    /// @param restFactory       Finds the volumes.
    /// @param dateTimeFormatter Formats when each volume was last written to.
    @Inject
    public IndexVolumeStatusListController(final EventBus eventBus,
                                           final IndexVolumeStatusList view,
                                           final RestFactory restFactory,
                                           final DateTimeFormatter dateTimeFormatter) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        initWidget(view);
        view.addColumns(dateTimeFormatter::formatWithDuration);
    }

    /// @return The selection.
    public MultiSelectionModel<IndexVolume> getSelectionModel() {
        return view.getSelectionModel();
    }

    /// The toolbar beside the list's pager, for the screen's own buttons.
    ///
    /// @return The toolbar.
    public Toolbar getToolbar() {
        return view.getToolbar();
    }

    /// Finds the volumes again.
    public void refresh() {
        this.consumer = null;
        dataProvider.refresh();
    }

    /// Starts the list.
    ///
    /// @param criteria Finds the volumes.
    /// @param c        Given the first page found, and no later ones.
    public void init(final ExpressionCriteria criteria, final Consumer<ResultPage<IndexVolume>> c) {
        this.consumer = c;
        dataProvider = new RestDataProvider<IndexVolume, ResultPage<IndexVolume>>(getEventBus()) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<IndexVolume>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                CriteriaUtil.setRange(criteria, range);
                restFactory
                        .create(INDEX_VOLUME_RESOURCE)
                        .method(res -> res.find(criteria))
                        .onSuccess(result -> {
                            dataConsumer.accept(result);
                            if (consumer != null) {
                                consumer.accept(result);
                            }
                        })
                        .onFailure(errorHandler)
                        .owner(IndexVolumeStatusListController.this)
                        .taskMonitorFactory(view.getTaskMonitor())
                        .exec();
            }
        };
        view.setDataProvider(dataProvider);
    }

    /// @return The task monitor that spins the list's pager's refresh button.
    public TaskMonitorFactory getTaskListener() {
        return view.getTaskMonitor();
    }
}
