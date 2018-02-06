/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.dashboard.spring;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import stroom.dashboard.server.DashboardService;
import stroom.dashboard.shared.Dashboard;
import stroom.explorer.server.ExplorerActionHandlers;
import stroom.importexport.server.ImportExportActionHandlers;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Exclude other configurations that might be found accidentally during a
 * component scan as configurations should be specified explicitly.
 */
@Configuration
@ComponentScan(basePackages = {"stroom.dashboard.server"}, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Configuration.class),})
public class DashboardConfiguration {
    @Inject
    public DashboardConfiguration(final ExplorerActionHandlers explorerActionHandlers,
                                  final ImportExportActionHandlers importExportActionHandlers,
                               final DashboardService dashboardService) {
        explorerActionHandlers.add(false, 7, Dashboard.ENTITY_TYPE, Dashboard.ENTITY_TYPE, dashboardService);
        importExportActionHandlers.add(Dashboard.ENTITY_TYPE, dashboardService);
    }
}
