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

import com.google.gwt.inject.client.AsyncProvider;
import stroom.jobsystem.client.presenter.JobListPresenter;
import stroom.monitoring.client.DatabaseTablesMonitoringPlugin;
import stroom.monitoring.client.JobListPlugin;
import stroom.monitoring.client.NodeMonitoringPlugin;
import stroom.monitoring.client.TaskProgressMonitoringPlugin;
import stroom.monitoring.client.presenter.DatabaseTablesMonitoringPresenter;
import stroom.monitoring.client.presenter.TaskProgressMonitoringPresenter;
import stroom.node.client.ManageNodeToolsPlugin;
import stroom.node.client.presenter.ManageGlobalPropertyEditPresenter;
import stroom.node.client.presenter.ManageGlobalPropertyListPresenter;
import stroom.node.client.presenter.ManageGlobalPropertyPresenter;
import stroom.node.client.presenter.ManageVolumesPresenter;
import stroom.node.client.presenter.NodeEditPresenter;
import stroom.node.client.presenter.NodeMonitoringPresenter;
import stroom.node.client.presenter.VolumeEditPresenter;

public interface MonitoringGinjector {
    AsyncProvider<DatabaseTablesMonitoringPlugin> getDatabaseTablesMonitoringPlugin();

    AsyncProvider<DatabaseTablesMonitoringPresenter> getDatabaseTablesMonitoringPresenter();

    AsyncProvider<JobListPlugin> getJobListPlugin();

    AsyncProvider<JobListPresenter> getJobListPresenter();

    AsyncProvider<NodeMonitoringPlugin> getNodeMonitoringPlugin();

    AsyncProvider<NodeMonitoringPresenter> getNodeMonitoringPresenter();

    AsyncProvider<ManageNodeToolsPlugin> getManageNodeToolsPlugin();

    AsyncProvider<ManageVolumesPresenter> getManageVolumesPresenter();

    AsyncProvider<NodeEditPresenter> getNodeEditPresenter();

    AsyncProvider<VolumeEditPresenter> getVolumeEditPresenter();

    // Global properties.
    AsyncProvider<ManageGlobalPropertyPresenter> getManageGlobalPropertyPresenter();

    AsyncProvider<ManageGlobalPropertyListPresenter> getManageGlobalPropertyListPresenter();

    AsyncProvider<ManageGlobalPropertyEditPresenter> getManageGlobalPropertyEditPresenter();

    AsyncProvider<TaskProgressMonitoringPlugin> getTaskProgressMonitoringPlugin();

    AsyncProvider<TaskProgressMonitoringPresenter> getTaskProgressMonitoringPresenter();
}
