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

import java.io.Serializable;

public class DashboardQueryKey implements Serializable {
    private String uuid;
    private long dashboardId;
    private String queryId;

    public DashboardQueryKey() {
    }

    public static DashboardQueryKey create(final String uuid, final long dashboardId, final String queryId) {
        final DashboardQueryKey dashboardQueryKey = new DashboardQueryKey();
        dashboardQueryKey.uuid = uuid;
        dashboardQueryKey.dashboardId = dashboardId;
        dashboardQueryKey.queryId = queryId;
        return dashboardQueryKey;
    }

    public String getUuid() {
        return uuid;
    }

    public long getDashboardId() {
        return dashboardId;
    }

    public String getQueryId() {
        return queryId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final DashboardQueryKey that = (DashboardQueryKey) o;

        if (dashboardId != that.dashboardId) return false;
        if (uuid != null ? !uuid.equals(that.uuid) : that.uuid != null) return false;
        return queryId != null ? queryId.equals(that.queryId) : that.queryId == null;
    }

    @Override
    public int hashCode() {
        int result = uuid != null ? uuid.hashCode() : 0;
        result = 31 * result + (int) (dashboardId ^ (dashboardId >>> 32));
        result = 31 * result + (queryId != null ? queryId.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DashboardQueryKey{" +
                "uuid='" + uuid + '\'' +
                ", dashboardId=" + dashboardId +
                ", queryId='" + queryId + '\'' +
                '}';
    }
}