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

package stroom.dashboard;


import stroom.dashboard.impl.DashboardStore;
import stroom.dashboard.impl.script.ScriptStore;
import stroom.dashboard.impl.visualisation.VisualisationStore;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.DashboardConfig;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.Dimension;
import stroom.dashboard.shared.LayoutConfig;
import stroom.dashboard.shared.SplitLayoutConfig;
import stroom.dashboard.shared.TabConfig;
import stroom.dashboard.shared.TabLayoutConfig;
import stroom.dashboard.shared.VisComponentSettings;
import stroom.docref.DocRef;
import stroom.script.shared.ScriptDoc;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.visualisation.shared.VisualisationDoc;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class TestDashboardStoreImpl extends AbstractCoreIntegrationTest {

    @Inject
    private DashboardStore dashboardStore;
    @Inject
    private VisualisationStore visualisationStore;
    @Inject
    private ScriptStore scriptStore;

    @Test
    void test() {
        final VisComponentSettings visSettings = getVisSettings();

        final DocRef dashboardRef = dashboardStore.createDocument("Test Dashboard");


        final List<ComponentConfig> components = new ArrayList<>();

        // ADD TEST DATA
        final List<LayoutConfig> downList = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            final List<LayoutConfig> acrossList = new ArrayList<>();
            for (int l = 0; l < 2; l++) {
                final List<LayoutConfig> down2List = new ArrayList<>();
                for (int j = 0; j < 3; j++) {
                    final List<TabConfig> tabConfigList = new ArrayList<>();
                    for (int k = 0; k < 2; k++) {
                        final String type = "table";
                        final String id = type + "_" + System.currentTimeMillis();

                        final ComponentConfig componentConfig = ComponentConfig.builder()
                                .type(type)
                                .id(id)
                                .name("table" + " " + i + ":" + j + ":" + k)
                                .settings(visSettings)
                                .build();
                        components.add(componentConfig);

                        final TabConfig tabConfig = new TabConfig(id, true);
                        tabConfigList.add(tabConfig);
                    }
                    final TabLayoutConfig tabLayoutConfig = TabLayoutConfig.builder().tabs(tabConfigList).build();
                    down2List.add(tabLayoutConfig);
                }
                final SplitLayoutConfig down2 = SplitLayoutConfig.builder().dimension(Dimension.Y).children(down2List).build();
                acrossList.add(down2);
            }
            final SplitLayoutConfig across = SplitLayoutConfig
                    .builder()
                    .dimension(Dimension.X)
                    .children(acrossList)
                    .build();
            downList.add(across);
        }
        // DONE - ADD TEST DATA
        final SplitLayoutConfig down = SplitLayoutConfig
                .builder()
                .dimension(Dimension.Y)
                .children(downList)
                .build();
        final DashboardConfig dashboardConfig = DashboardConfig.builder()
                .components(components)
                .layout(down)
                .build();

        DashboardDoc dashboard = dashboardStore.readDocument(dashboardRef)
                .copy().dashboardConfig(dashboardConfig).build();

        dashboard = dashboardStore.writeDocument(dashboard);

        System.out.println(dashboard.getDashboardConfig());

        dashboardStore.readDocument(dashboardRef);
    }

    private VisComponentSettings getVisSettings() {
        final DocRef scriptRef = scriptStore.createDocument("Test");
        final ScriptDoc script = scriptStore.readDocument(scriptRef).copy().data("Test").build();
        scriptStore.writeDocument(script);

        final DocRef visRef = visualisationStore.createDocument("Test");
        final VisualisationDoc vis = visualisationStore.readDocument(visRef);
        vis.setScriptRef(scriptRef);
        visualisationStore.writeDocument(vis);

        return VisComponentSettings.builder()
                .visualisation(visRef)
                .build();
    }
}
