/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dashboard.shared;

import java.util.Objects;

public class DashboardQueryKey {
    private String uuid;
    private String dashboardUuid;
    private String componentId;

    public DashboardQueryKey() {
    }

    public static DashboardQueryKey create(final String uuid, final String dashboardUuid, final String componentId) {
        final DashboardQueryKey dashboardQueryKey = new DashboardQueryKey();
        dashboardQueryKey.uuid = uuid;
        dashboardQueryKey.dashboardUuid = dashboardUuid;
        dashboardQueryKey.componentId = componentId;
        return dashboardQueryKey;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(final String uuid) {
        this.uuid = uuid;
    }

    public String getDashboardUuid() {
        return dashboardUuid;
    }

    public void setDashboardUuid(final String dashboardUuid) {
        this.dashboardUuid = dashboardUuid;
    }

    public String getComponentId() {
        return componentId;
    }

    public void setComponentId(final String componentId) {
        this.componentId = componentId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DashboardQueryKey that = (DashboardQueryKey) o;
        return Objects.equals(uuid, that.uuid) &&
                Objects.equals(dashboardUuid, that.dashboardUuid) &&
                Objects.equals(componentId, that.componentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, dashboardUuid, componentId);
    }

    @Override
    public String toString() {
        return "DashboardQueryKey{" +
                "uuid='" + uuid + '\'' +
                ", dashboardUuid=" + dashboardUuid +
                ", componentId='" + componentId + '\'' +
                '}';
    }
}