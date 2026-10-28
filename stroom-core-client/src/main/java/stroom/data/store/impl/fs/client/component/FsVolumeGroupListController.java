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

package stroom.data.store.impl.fs.client.component;

import stroom.data.client.CriteriaUtil;
import stroom.data.client.RestDataProvider;
import stroom.data.store.impl.fs.shared.FsVolumeGroupResource;
import stroom.data.store.impl.fs.shared.FsVolumeGroupRow;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.entity.shared.ExpressionCriteria;
import stroom.util.shared.ResultPage;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// The data volume groups ([FsVolumeGroupList]), a page at a time.
public class FsVolumeGroupListController extends Controller {

    private static final FsVolumeGroupResource FS_VOLUME_GROUP_RESOURCE =
            GWT.create(FsVolumeGroupResource.class);

    private final FsVolumeGroupList view;
    private final RestDataProvider<FsVolumeGroupRow, ResultPage<FsVolumeGroupRow>> dataProvider;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus    The application's event bus.
    /// @param view        The content.
    /// @param restFactory Finds the groups.
    @Inject
    public FsVolumeGroupListController(final EventBus eventBus,
                                       final FsVolumeGroupList view,
                                       final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        initWidget(view);

        final ExpressionCriteria criteria = new ExpressionCriteria();
        dataProvider = new RestDataProvider<FsVolumeGroupRow, ResultPage<FsVolumeGroupRow>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<FsVolumeGroupRow>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                CriteriaUtil.setRange(criteria, range);
                restFactory
                        .create(FS_VOLUME_GROUP_RESOURCE)
                        .method(resource ->
                                resource.findExtended(criteria))
                        .onSuccess(dataConsumer)
                        .onFailure(errorHandler)
                        .owner(FsVolumeGroupListController.this)
                        .taskMonitorFactory(view.getTaskMonitor())
                        .exec();
            }
        };
        view.setDataProvider(dataProvider);
    }

    /// @return The selection.
    public MultiSelectionModel<FsVolumeGroupRow> getSelectionModel() {
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
