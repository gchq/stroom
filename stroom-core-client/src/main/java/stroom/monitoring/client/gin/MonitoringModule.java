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
import stroom.data.store.impl.fs.client.ManageFsVolumesPlugin;
import stroom.index.client.ManageIndexVolumesPlugin;
import stroom.monitoring.client.DatabaseTablesMonitoringPlugin;
import stroom.monitoring.client.ExecutionSchedulesPlugin;
import stroom.monitoring.client.JobsPlugin;
import stroom.monitoring.client.NodeGroupsPlugin;
import stroom.monitoring.client.NodesPlugin;
import stroom.monitoring.client.ProcessorProfilesPlugin;
import stroom.node.client.ManageGlobalPropertiesPlugin;
import stroom.task.client.ServerTasksPlugin;
import stroom.ui.config.client.UiConfigCache;

public class MonitoringModule extends PluginModule {

    @Override
    protected void configure() {
        bind(UiConfigCache.class).asEagerSingleton();

        bindPlugin(DatabaseTablesMonitoringPlugin.class);

        // Job management.
        bindPlugin(JobsPlugin.class);
        bindPlugin(ExecutionSchedulesPlugin.class);

        // Node management.
        bindPlugin(NodeGroupsPlugin.class);
        bindPlugin(NodesPlugin.class);
        bindPlugin(ProcessorProfilesPlugin.class);

        bindPlugin(ManageFsVolumesPlugin.class);
        bindPlugin(ManageIndexVolumesPlugin.class);
        bindPlugin(ManageGlobalPropertiesPlugin.class);

        bindPlugin(ServerTasksPlugin.class);
    }
}
