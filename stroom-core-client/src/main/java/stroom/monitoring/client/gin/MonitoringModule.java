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

import stroom.core.client.gin.PluginModule;
import stroom.jobsystem.client.presenter.JobPresenter;
import stroom.jobsystem.client.presenter.JobPresenter.JobView;
import stroom.jobsystem.client.view.JobViewImpl;
import stroom.monitoring.client.DatabaseTablesMonitoringPlugin;
import stroom.monitoring.client.JobListPlugin;
import stroom.monitoring.client.NodeMonitoringPlugin;
import stroom.monitoring.client.TaskProgressMonitoringPlugin;
import stroom.monitoring.client.presenter.SchedulePresenter;
import stroom.monitoring.client.presenter.SchedulePresenter.ScheduleView;
import stroom.monitoring.client.view.ScheduleViewImpl;
import stroom.node.client.ClientPropertyCache;
import stroom.node.client.ManageNodeToolsPlugin;
import stroom.node.client.presenter.ManageGlobalPropertyEditPresenter;
import stroom.node.client.presenter.ManageGlobalPropertyListPresenter;
import stroom.node.client.presenter.ManageGlobalPropertyPresenter;
import stroom.node.client.presenter.ManageVolumesPresenter;
import stroom.node.client.presenter.ManageVolumesPresenter.ManageVolumesProxy;
import stroom.node.client.presenter.NodeEditPresenter;
import stroom.node.client.presenter.VolumeEditPresenter;
import stroom.node.client.view.GlobalPropertyEditViewImpl;
import stroom.node.client.view.NodeEditViewImpl;
import stroom.node.client.view.VolumeEditViewImpl;
import stroom.node.client.view.WrapperView;
import stroom.node.client.view.WrapperViewImpl;

public class MonitoringModule extends PluginModule {
    @Override
    protected void configure() {
        bind(ClientPropertyCache.class).asEagerSingleton();

        bindPlugin(DatabaseTablesMonitoringPlugin.class);

        // Job management.
        bindPlugin(JobListPlugin.class);
        bindPresenterWidget(SchedulePresenter.class, ScheduleView.class, ScheduleViewImpl.class);

        // Node management.
        bindPlugin(NodeMonitoringPlugin.class);

        bindPlugin(ManageNodeToolsPlugin.class);

        bindSharedView(WrapperView.class, WrapperViewImpl.class);
        bind(ManageVolumesPresenter.class);
        bind(ManageVolumesProxy.class).asEagerSingleton();

        bindPresenterWidget(VolumeEditPresenter.class, VolumeEditPresenter.VolumeEditView.class,
                VolumeEditViewImpl.class);
        bindPresenterWidget(NodeEditPresenter.class, NodeEditPresenter.NodeEditView.class, NodeEditViewImpl.class);

        bindPresenterWidget(JobPresenter.class, JobView.class, JobViewImpl.class);

        // Global properties.
        bind(ManageGlobalPropertyPresenter.class);
        bind(ManageGlobalPropertyListPresenter.class);
        bindPresenterWidget(ManageGlobalPropertyEditPresenter.class,
                ManageGlobalPropertyEditPresenter.GlobalPropertyEditView.class, GlobalPropertyEditViewImpl.class);

        bindPlugin(TaskProgressMonitoringPlugin.class);
    }
}
