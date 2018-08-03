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
 */

package stroom.dashboard.client.main;

import stroom.util.client.RandomId;

public class DashboardUUID {
    private String dashboardUuid;
    private String dashboardName;
    private String componentId;

    public DashboardUUID(String dashboardUuid, final String dashboardName, final String componentId) {
        this.dashboardUuid = dashboardUuid;
        this.dashboardName = dashboardName;
        this.componentId = componentId;
    }

    public String getDashboardUuid() {
        return dashboardUuid;
    }

    public String getComponentId() {
        return componentId;
    }

    public String getUUID() {
        return dashboardUuid + "|" + dashboardName + "|" + componentId + "|" + RandomId.createDiscrimiator();
    }
}
