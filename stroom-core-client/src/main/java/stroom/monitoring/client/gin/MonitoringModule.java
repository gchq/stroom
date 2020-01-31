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

package stroom.monitoring.client.gin;

import stroom.config.global.client.presenter.ManageGlobalPropertyEditPresenter;
import stroom.config.global.client.presenter.ManageGlobalPropertyListPresenter;
import stroom.config.global.client.presenter.ManageGlobalPropertyPresenter;
import stroom.config.global.client.view.GlobalPropertyEditViewImpl;
import stroom.config.global.client.view.ManageGlobalPropertyViewImpl;
import stroom.core.client.gin.PluginModule;
import stroom.job.client.presenter.JobPresenter;
import stroom.job.client.presenter.JobPresenter.JobView;
import stroom.job.client.view.JobViewImpl;
import stroom.monitoring.client.DatabaseTablesMonitoringPlugin;
import stroom.monitoring.client.JobListPlugin;
import stroom.monitoring.client.NodeMonitoringPlugin;
import stroom.task.client.TaskManagerPlugin;
import stroom.job.client.presenter.SchedulePresenter;
import stroom.job.client.presenter.SchedulePresenter.ScheduleView;
import stroom.task.client.presenter.TaskManagerPresenter;
import stroom.task.client.presenter.TaskManagerPresenter.TaskManagerView;
import stroom.job.client.view.ScheduleViewImpl;
import stroom.task.client.view.TaskManagerViewImpl;
import stroom.node.client.ManageNodeToolsPlugin;
import stroom.node.client.presenter.NodeEditPresenter;
import stroom.node.client.view.NodeEditViewImpl;
import stroom.node.client.view.WrapperView;
import stroom.node.client.view.WrapperViewImpl;
import stroom.ui.config.client.UiConfigCache;

public class MonitoringModule extends PluginModule {
    @Override
    protected void configure() {
        bind(UiConfigCache.class).asEagerSingleton();

        bindPlugin(DatabaseTablesMonitoringPlugin.class);

        // Job management.
        bindPlugin(JobListPlugin.class);
        bindPresenterWidget(SchedulePresenter.class, ScheduleView.class, ScheduleViewImpl.class);

        // Node management.
        bindPlugin(NodeMonitoringPlugin.class);

        bindPlugin(ManageNodeToolsPlugin.class);

        bindSharedView(WrapperView.class, WrapperViewImpl.class);

        bindPresenterWidget(NodeEditPresenter.class, NodeEditPresenter.NodeEditView.class, NodeEditViewImpl.class);

        bindPresenterWidget(JobPresenter.class, JobView.class, JobViewImpl.class);

        // Global properties.
        bind(ManageGlobalPropertyListPresenter.class);
        bindPresenterWidget(ManageGlobalPropertyPresenter.class, ManageGlobalPropertyPresenter.ManageGlobalPropertyView.class, ManageGlobalPropertyViewImpl.class);
        bindPresenterWidget(ManageGlobalPropertyEditPresenter.class, ManageGlobalPropertyEditPresenter.GlobalPropertyEditView.class, GlobalPropertyEditViewImpl.class);

        bindPlugin(TaskManagerPlugin.class);
        bindPresenterWidget(TaskManagerPresenter.class, TaskManagerView.class, TaskManagerViewImpl.class);
    }
}
