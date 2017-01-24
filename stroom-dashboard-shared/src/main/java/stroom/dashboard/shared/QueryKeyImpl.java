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

import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;
import stroom.util.shared.SharedObject;

public class QueryKeyImpl implements QueryKey, SharedObject {
    private static final long serialVersionUID = -3222989872764402068L;

    private long dashboardId;
    private String dashboardName;
    private String queryId;

    public QueryKeyImpl() {
        // Default constructor necessary for GWT serialisation.
    }

    public QueryKeyImpl(final long dashboardId, final String dashboardName, final String queryId) {
        this.dashboardId = dashboardId;
        this.dashboardName = dashboardName;
        this.queryId = queryId;
    }

    public long getDashboardId() {
        return dashboardId;
    }

    public String getDashboardName() {
        return dashboardName;
    }

    public String getQueryId() {
        return queryId;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof QueryKeyImpl)) {
            return false;
        }

        final QueryKeyImpl queryKey = (QueryKeyImpl) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(dashboardId, queryKey.dashboardId);
        builder.append(dashboardName, queryKey.dashboardName);
        builder.append(queryId, queryKey.queryId);
        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(dashboardId);
        builder.append(dashboardName);
        builder.append(queryId);
        return builder.toHashCode();
    }

    @Override
    public String toString() {
        return dashboardName + "(" + dashboardId + ") " + queryId;
    }
}
