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

package stroom.dashboard.shared;

import stroom.util.shared.SharedObject;

public class DashboardQueryKey implements SharedObject {
    private static final long serialVersionUID = -3222989872764402068L;

    private String uuid;
    private long dashboardId;

    public DashboardQueryKey() {
        // Default constructor necessary for GWT serialisation.
    }

    public DashboardQueryKey(final String uuid, final long dashboardId) {
        this.uuid = uuid;
        this.dashboardId = dashboardId;
    }

    public String getUuid() {
        return uuid;
    }

    public long getDashboardId() {
        return dashboardId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final DashboardQueryKey that = (DashboardQueryKey) o;

        if (dashboardId != that.dashboardId) return false;
        return uuid != null ? uuid.equals(that.uuid) : that.uuid == null;
    }

    @Override
    public int hashCode() {
        int result = uuid != null ? uuid.hashCode() : 0;
        result = 31 * result + (int) (dashboardId ^ (dashboardId >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "DashboardQueryKey{" +
                "uuid='" + uuid + '\'' +
                ", dashboardId=" + dashboardId +
                '}';
    }
}