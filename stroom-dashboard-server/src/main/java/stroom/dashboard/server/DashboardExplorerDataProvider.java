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

package stroom.dashboard.server;

import org.springframework.stereotype.Component;
import stroom.dashboard.shared.Dashboard;
import stroom.dashboard.shared.FindDashboardCriteria;
import stroom.entity.server.FolderService;
import stroom.explorer.server.AbstractExplorerDataProvider;
import stroom.explorer.server.ProvidesExplorerData;
import stroom.explorer.server.TreeModel;

import javax.inject.Inject;
import javax.inject.Named;

@ProvidesExplorerData
@Component
public class DashboardExplorerDataProvider extends AbstractExplorerDataProvider<Dashboard, FindDashboardCriteria> {
    private final DashboardService dashboardService;

    @Inject
    DashboardExplorerDataProvider(@Named("cachedFolderService") final FolderService cachedFolderService, final DashboardService dashboardService) {
        super(cachedFolderService);
        this.dashboardService = dashboardService;
    }

    @Override
    public void addItems(final TreeModel treeModel) {
        addItems(dashboardService, treeModel);
    }

    @Override
    public String getType() {
        return Dashboard.ENTITY_TYPE;
    }

    @Override
    public String getDisplayType() {
        return Dashboard.ENTITY_TYPE;
    }

    @Override
    public int getPriority() {
        return 7;
    }
}
