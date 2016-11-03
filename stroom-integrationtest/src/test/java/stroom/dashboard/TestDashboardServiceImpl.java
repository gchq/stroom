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

package stroom.dashboard;

import stroom.AbstractCoreIntegrationTest;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.Dashboard;
import stroom.dashboard.shared.DashboardConfig;
import stroom.dashboard.shared.DashboardService;
import stroom.dashboard.shared.SplitLayoutConfig;
import stroom.dashboard.shared.SplitLayoutConfig.Direction;
import stroom.dashboard.shared.TabConfig;
import stroom.dashboard.shared.TabLayoutConfig;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.FolderService;
import stroom.entity.shared.Res;
import stroom.query.shared.VisDashboardSettings;
import stroom.script.shared.Script;
import stroom.script.shared.ScriptService;
import stroom.visualisation.shared.Visualisation;
import stroom.visualisation.shared.VisualisationService;
import org.junit.Test;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

public class TestDashboardServiceImpl extends AbstractCoreIntegrationTest {
    @Resource
    private DashboardService dashboardService;
    @Resource
    private VisualisationService visualisationService;
    @Resource
    private ScriptService scriptService;
    @Resource
    private FolderService folderService;

    @Test
    public void test() {
        final DocRef testGroup = DocRef.create(folderService.create(null, "Test Group"));

        final VisDashboardSettings visSettings = getVisSettings(testGroup);

        Dashboard dashboard = dashboardService.create(testGroup, "Test Dashboard");

        final List<ComponentConfig> components = new ArrayList<>();

        // ADD TEST DATA
        final SplitLayoutConfig down = new SplitLayoutConfig(Direction.DOWN.getDimension());
        for (int i = 0; i < 3; i++) {
            final SplitLayoutConfig across = new SplitLayoutConfig(Direction.ACROSS.getDimension());
            down.add(across);

            for (int l = 0; l < 2; l++) {
                final SplitLayoutConfig down2 = new SplitLayoutConfig(Direction.DOWN.getDimension());
                across.add(down2);

                for (int j = 0; j < 3; j++) {
                    final TabLayoutConfig tablayout = new TabLayoutConfig();
                    down2.add(tablayout);

                    for (int k = 0; k < 2; k++) {
                        final String type = "table";
                        final String id = type + "_" + String.valueOf(System.currentTimeMillis());

                        final ComponentConfig componentData = new ComponentConfig();
                        componentData.setType(type);
                        componentData.setId(id);
                        componentData.setName("table" + " " + i + ":" + j + ":" + k);
                        componentData.setSettings(visSettings);
                        components.add(componentData);

                        final TabConfig tabData = new TabConfig();
                        tabData.setId(id);

                        tablayout.add(tabData);
                    }
                }
            }
        }
        // DONE - ADD TEST DATA

        final DashboardConfig dashboardData = new DashboardConfig();
        dashboardData.setComponents(components);
        dashboardData.setLayout(down);

        dashboard.setDashboardData(dashboardData);

        dashboard = dashboardService.save(dashboard);

        System.out.println(dashboard.getData());

        dashboard = dashboardService.load(dashboard);
    }

    private VisDashboardSettings getVisSettings(final DocRef testGroup) {
        final Res res = new Res();
        res.setData("Test");

        Script script = scriptService.create(testGroup, "Test");
        script.setResource(res);
        script = scriptService.save(script);

        Visualisation vis = visualisationService.create(testGroup, "Test");
        vis.setScriptRef(DocRef.create(script));
        vis = visualisationService.save(vis);

        final VisDashboardSettings visSettings = new VisDashboardSettings();
        visSettings.setVisualisation(DocRef.create(vis));

        return visSettings;
    }
}
