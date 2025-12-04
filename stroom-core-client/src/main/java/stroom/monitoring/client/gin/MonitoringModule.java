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

package stroom.monitoring.client.gin;

import stroom.config.global.client.presenter.ConfigPropertyClusterValuesListPresenter;
import stroom.config.global.client.presenter.ConfigPropertyClusterValuesPresenter;
import stroom.config.global.client.presenter.GlobalPropertyTabPresenter;
import stroom.config.global.client.presenter.ManageGlobalPropertyEditPresenter;
import stroom.config.global.client.presenter.ManageGlobalPropertyListPresenter;
import stroom.config.global.client.view.ConfigPropertyClusterValuesViewImpl;
import stroom.config.global.client.view.GlobalPropertyEditViewImpl;
import stroom.config.global.client.view.GlobalPropertyTabViewImpl;
import stroom.core.client.gin.PluginModule;
import stroom.data.grid.client.WrapperView;
import stroom.data.grid.client.WrapperViewImpl;
import stroom.data.store.impl.fs.client.ManageFsVolumesPlugin;
import stroom.index.client.ManageIndexVolumesPlugin;
import stroom.job.client.presenter.JobPresenter;
import stroom.job.client.presenter.JobPresenter.JobView;
import stroom.job.client.view.JobViewImpl;
import stroom.monitoring.client.DatabaseTablesMonitoringPlugin;
import stroom.monitoring.client.JobListPlugin;
import stroom.monitoring.client.NodeMonitoringPlugin;
import stroom.node.client.ManageGlobalPropertiesPlugin;
import stroom.node.client.presenter.NodePresenter;
import stroom.node.client.presenter.NodePresenter.NodeView;
import stroom.node.client.view.NodeViewImpl;
import stroom.schedule.client.SchedulePopup;
import stroom.schedule.client.SchedulePopup.ScheduleView;
import stroom.schedule.client.ScheduleViewImpl;
import stroom.task.client.TaskManagerPlugin;
import stroom.task.client.presenter.TaskManagerPresenter;
import stroom.task.client.presenter.TaskManagerPresenter.TaskManagerView;
import stroom.task.client.view.TaskManagerViewImpl;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.datepicker.client.DateTimePopup;
import stroom.widget.datepicker.client.DateTimePopup.DateTimeView;
import stroom.widget.datepicker.client.DateTimeViewImpl;

public class MonitoringModule extends PluginModule {

    @Override
    protected void configure() {
        bind(UiConfigCache.class).asEagerSingleton();

        bindPlugin(DatabaseTablesMonitoringPlugin.class);

        // Job management.
        bindPlugin(JobListPlugin.class);
        bindPresenterWidget(
                SchedulePopup.class,
                ScheduleView.class,
                ScheduleViewImpl.class);
        bindPresenterWidget(
                DateTimePopup.class,
                DateTimeView.class,
                DateTimeViewImpl.class);

        // Node management.
        bindPlugin(NodeMonitoringPlugin.class);

        bindPresenterWidget(
                NodePresenter.class,
                NodeView.class,
                NodeViewImpl.class);

        bindPlugin(ManageFsVolumesPlugin.class);
        bindPlugin(ManageIndexVolumesPlugin.class);
        bindPlugin(ManageGlobalPropertiesPlugin.class);

        bindSharedView(WrapperView.class, WrapperViewImpl.class);

        bindPresenterWidget(
                JobPresenter.class,
                JobView.class,
                JobViewImpl.class);

        // Global properties.
        bind(ManageGlobalPropertyListPresenter.class);

        bind(ConfigPropertyClusterValuesListPresenter.class);

        bindPresenterWidget(
                GlobalPropertyTabPresenter.class,
                GlobalPropertyTabPresenter.GlobalPropertyTabView.class,
                GlobalPropertyTabViewImpl.class);

        bindPresenterWidget(
                ManageGlobalPropertyEditPresenter.class,
                ManageGlobalPropertyEditPresenter.GlobalPropertyEditView.class,
                GlobalPropertyEditViewImpl.class);

        bindPresenterWidget(
                ConfigPropertyClusterValuesPresenter.class,
                ConfigPropertyClusterValuesPresenter.ConfigPropertyClusterValuesView.class,
                ConfigPropertyClusterValuesViewImpl.class);

        bindPlugin(TaskManagerPlugin.class);
        bindPresenterWidget(
                TaskManagerPresenter.class,
                TaskManagerView.class,
                TaskManagerViewImpl.class);
    }
}
