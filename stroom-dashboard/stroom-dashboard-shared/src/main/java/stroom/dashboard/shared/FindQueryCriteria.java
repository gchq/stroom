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

package stroom.dashboard.shared;

import stroom.entity.shared.FindDocumentEntityCriteria;

public class FindQueryCriteria extends FindDocumentEntityCriteria {
    public static final String FIELD_TIME = "Time";
    private static final long serialVersionUID = -4421720204507720754L;
    private String dashboardUuid;
    private String queryId;
    private Boolean favourite;

    public FindQueryCriteria() {
        // Default constructor necessary for GWT serialisation.
    }

    public FindQueryCriteria(final String name) {
        super(name);
    }

    public String getDashboardUuid() {
        return dashboardUuid;
    }

    public void setDashboardUuid(final String dashboardUuid) {
        this.dashboardUuid = dashboardUuid;
    }

    public String getQueryId() {
        return queryId;
    }

    public void setQueryId(final String queryId) {
        this.queryId = queryId;
    }

    public Boolean getFavourite() {
        return favourite;
    }

    public void setFavourite(final Boolean favourite) {
        this.favourite = favourite;
    }
}
