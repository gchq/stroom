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

package stroom.index.client.component;

import stroom.dispatch.client.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.entity.shared.ExpressionCriteria;
import stroom.index.shared.IndexVolumeGroup;
import stroom.index.shared.IndexVolumeGroupResource;
import stroom.util.shared.ResultPage;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Controller;
import stroom.widget.datagrid.client.CriteriaUtil;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// The index volume groups ([IndexVolumeGroupList]), a page at a time.
public class IndexVolumeGroupListController extends Controller {

    private static final IndexVolumeGroupResource INDEX_VOLUME_GROUP_RESOURCE =
            GWT.create(IndexVolumeGroupResource.class);

    private final IndexVolumeGroupList view;
    private final RestDataProvider<IndexVolumeGroup, ResultPage<IndexVolumeGroup>> dataProvider;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus    The application's event bus.
    /// @param view        The content.
    /// @param restFactory Finds the groups.
    @Inject
    public IndexVolumeGroupListController(final EventBus eventBus,
                                          final IndexVolumeGroupList view,
                                          final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        initWidget(view);

        final ExpressionCriteria criteria = new ExpressionCriteria();
        dataProvider = new RestDataProvider<IndexVolumeGroup, ResultPage<IndexVolumeGroup>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<IndexVolumeGroup>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                CriteriaUtil.setRange(criteria, range);
                restFactory
                        .create(INDEX_VOLUME_GROUP_RESOURCE)
                        .method(res -> res.find(criteria))
                        .onSuccess(dataConsumer)
                        .onFailure(errorHandler)
                        .owner(IndexVolumeGroupListController.this)
                        .taskMonitorFactory(view.getTaskMonitor())
                        .exec();
            }
        };
        view.setDataProvider(dataProvider);
    }

    /// @return The selection.
    public MultiSelectionModel<IndexVolumeGroup> getSelectionModel() {
        return view.getSelectionModel();
    }

    /// The toolbar beside the list's pager, for the screen's own buttons.
    ///
    /// @return The toolbar.
    public Toolbar getToolbar() {
        return view.getToolbar();
    }

    /// Finds the groups again.
    public void refresh() {
        dataProvider.refresh();
    }
}
