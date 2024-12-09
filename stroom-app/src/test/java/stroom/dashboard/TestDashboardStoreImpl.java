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

package stroom.dashboard;


import stroom.dashboard.impl.DashboardStore;
import stroom.dashboard.impl.script.ScriptStore;
import stroom.dashboard.impl.visualisation.VisualisationStore;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.DashboardConfig;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.Dimension;
import stroom.dashboard.shared.SplitLayoutConfig;
import stroom.dashboard.shared.TabConfig;
import stroom.dashboard.shared.TabLayoutConfig;
import stroom.dashboard.shared.VisComponentSettings;
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

        final List<ComponentConfig> components = new ArrayList<>();

        // ADD TEST DATA
        final SplitLayoutConfig down = new SplitLayoutConfig(Dimension.Y);
        for (int i = 0; i < 3; i++) {
            final SplitLayoutConfig across = new SplitLayoutConfig(Dimension.X);
            down.add(across);

            for (int l = 0; l < 2; l++) {
                final SplitLayoutConfig down2 = new SplitLayoutConfig(Dimension.Y);
                across.add(down2);

                for (int j = 0; j < 3; j++) {
                    final TabLayoutConfig tablayout = new TabLayoutConfig();
                    down2.add(tablayout);

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
                        tablayout.add(tabConfig);
                    }
                }
            }
        }
        // DONE - ADD TEST DATA

        final DashboardConfig dashboardData = new DashboardConfig();
        dashboardData.setComponents(components);
        dashboardData.setLayout(down);

        DashboardDoc dashboard = dashboardStore.createDocument();
        dashboard.setName("Test Dashboard");
        dashboard.setDashboardConfig(dashboardData);
        dashboard = dashboardStore.writeDocument(dashboard);

        System.out.println(dashboard.getDashboardConfig());

        dashboardStore.readDocument(dashboard.asDocRef());
    }

    private VisComponentSettings getVisSettings() {
        ScriptDoc script = scriptStore.createDocument();
        script.setName("Test");
        script.setData("Test");
        script = scriptStore.writeDocument(script);

        VisualisationDoc vis = visualisationStore.createDocument();
        vis.setName("Test");
        vis.setScriptRef(script.asDocRef());
        vis = visualisationStore.writeDocument(vis);

        return VisComponentSettings.builder()
                .visualisation(vis.asDocRef())
                .build();
    }
}
