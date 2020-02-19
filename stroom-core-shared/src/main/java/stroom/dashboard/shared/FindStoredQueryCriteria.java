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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.FindDocumentEntityCriteria;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Sort;
import stroom.util.shared.StringCriteria;

import java.util.List;

@JsonInclude(Include.NON_DEFAULT)
public class FindStoredQueryCriteria extends FindDocumentEntityCriteria {
    public static final String FIELD_ID = "Id";
    public static final String FIELD_TIME = "Time";

    @JsonProperty
    private String userId;
    @JsonProperty
    private String dashboardUuid;
    @JsonProperty
    private String componentId;
    @JsonProperty
    private Boolean favourite;

    public FindStoredQueryCriteria() {
        // Default constructor necessary for GWT serialisation.
    }

    public FindStoredQueryCriteria(final String name) {
        super(name);
    }

    @JsonCreator
    public FindStoredQueryCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                   @JsonProperty("sortList") final List<Sort> sortList,
                                   @JsonProperty("name") final StringCriteria name,
                                   @JsonProperty("requiredPermission") final String requiredPermission,
                                   @JsonProperty("userId") final String userId,
                                   @JsonProperty("dashboardUuid") final String dashboardUuid,
                                   @JsonProperty("componentId") final String componentId,
                                   @JsonProperty("favourite") final Boolean favourite) {
        super(pageRequest, sortList, name, requiredPermission);
        this.userId = userId;
        this.dashboardUuid = dashboardUuid;
        this.componentId = componentId;
        this.favourite = favourite;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(final String userId) {
        this.userId = userId;
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

    public Boolean getFavourite() {
        return favourite;
    }

    public void setFavourite(final Boolean favourite) {
        this.favourite = favourite;
    }
}
