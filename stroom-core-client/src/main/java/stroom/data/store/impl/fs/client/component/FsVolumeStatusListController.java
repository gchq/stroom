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

package stroom.data.store.impl.fs.client.component;

import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.data.store.impl.fs.shared.FsVolumeResource;
import stroom.dispatch.client.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Selection;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Controller;
import stroom.widget.datagrid.client.CriteriaUtil;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;

/// The volumes of the data volume group its owner gives ([FsVolumeStatusList]), a page at a time.
public class FsVolumeStatusListController extends Controller {

    private static final FsVolumeResource FS_VOLUME_RESOURCE = GWT.create(FsVolumeResource.class);

    private final RestFactory restFactory;
    private final DateTimeFormatter dateTimeFormatter;
    private FsVolumeStatusList view;
    private FindFsVolumeCriteria criteria;
    private RestDataProvider<FsVolume, ResultPage<FsVolume>> dataProvider;

    /// Made by GIN; [#attach(FsVolumeStatusList)] gives it its list.
    ///
    /// @param eventBus          The application's event bus.
    /// @param restFactory       Finds the volumes.
    /// @param dateTimeFormatter Formats when each volume was last written to.
    @Inject
    public FsVolumeStatusListController(final EventBus eventBus,
                                        final RestFactory restFactory,
                                        final DateTimeFormatter dateTimeFormatter) {
        super(eventBus);
        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;
    }

    /// Attaches this to a list, e.g. one a template declares. The list is then this
    /// controller's widget.
    ///
    /// @param view The list.
    /// @return This, for its owner to keep.
    public FsVolumeStatusListController attach(final FsVolumeStatusList view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);
        view.addColumns(dateTimeFormatter::formatWithDuration);
        return this;
    }

    /// Shows a group's volumes; the first time, starts the list.
    ///
    /// @param group The group.
    public void setGroup(final FsVolumeGroup group) {
        criteria = new FindFsVolumeCriteria(
                null,
                group,
                null,
                Selection.selectAll());
        if (dataProvider == null) {
            dataProvider = new RestDataProvider<FsVolume, ResultPage<FsVolume>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<FsVolume>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    CriteriaUtil.setRange(criteria, range);
                    restFactory
                            .create(FS_VOLUME_RESOURCE)
                            .method(resource ->
                                    resource.find(criteria))
                            .onSuccess(dataConsumer)
                            .onFailure(errorHandler)
                            .owner(FsVolumeStatusListController.this)
                            .taskMonitorFactory(view.getTaskMonitor())
                            .exec();
                }
            };
            view.setDataProvider(dataProvider);
        } else {
            dataProvider.refresh();
        }
    }

    /// @return The selection.
    public MultiSelectionModel<FsVolume> getSelectionModel() {
        return view.getSelectionModel();
    }

    /// The toolbar beside the list's pager, for the screen's own buttons.
    ///
    /// @return The toolbar.
    public Toolbar getToolbar() {
        return view.getToolbar();
    }

    /// Finds the volumes again, once a group has been given.
    public void refresh() {
        if (dataProvider != null) {
            dataProvider.refresh();
        }
    }
}
