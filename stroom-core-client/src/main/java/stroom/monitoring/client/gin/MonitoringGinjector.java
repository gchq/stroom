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
import stroom.index.client.ManageIndexVolumesPlugin;
import stroom.job.client.presenter.JobListPresenter;
import stroom.monitoring.client.DatabaseTablesMonitoringPlugin;
import stroom.monitoring.client.JobListPlugin;
import stroom.monitoring.client.NodeMonitoringPlugin;
import stroom.monitoring.client.presenter.DatabaseTablesMonitoringPresenter;
import stroom.node.client.presenter.NodePresenter;
import stroom.task.client.TaskManagerPlugin;
import stroom.task.client.presenter.TaskManagerPresenter;

import com.google.gwt.inject.client.AsyncProvider;

public interface MonitoringGinjector {

    AsyncProvider<DatabaseTablesMonitoringPlugin> getDatabaseTablesMonitoringPlugin();

    AsyncProvider<DatabaseTablesMonitoringPresenter> getDatabaseTablesMonitoringPresenter();

    AsyncProvider<JobListPlugin> getJobListPlugin();

    AsyncProvider<JobListPresenter> getJobListPresenter();

    AsyncProvider<NodeMonitoringPlugin> getNodeMonitoringPlugin();

    AsyncProvider<NodePresenter> getNodeMonitoringPresenter();

    AsyncProvider<ManageIndexVolumesPlugin> getManageIndexVolumesPlugin();

    // Global properties.
    AsyncProvider<GlobalPropertyTabPresenter> getManageGlobalPropertyPresenter();

    AsyncProvider<ManageGlobalPropertyListPresenter> getManageGlobalPropertyListPresenter();

    AsyncProvider<ManageGlobalPropertyEditPresenter> getManageGlobalPropertyEditPresenter();

    AsyncProvider<ConfigPropertyClusterValuesPresenter> getConfigPropertyClusterValuesPresenter();

    AsyncProvider<ConfigPropertyClusterValuesListPresenter> getConfigPropertyClusterValuesListPresenter();

    AsyncProvider<TaskManagerPlugin> getTaskProgressMonitoringPlugin();

    AsyncProvider<TaskManagerPresenter> getTaskProgressMonitoringPresenter();
}
