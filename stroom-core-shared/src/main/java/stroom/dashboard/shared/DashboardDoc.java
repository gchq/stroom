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

import stroom.docref.DocRef;
import stroom.docs.shared.Description;
import stroom.docstore.shared.AbstractDoc;
import stroom.svg.shared.SvgImage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@Description(
        "The Dashboard Document defines a data querying and visualisation dashboard.\n" +
                "The dashboard is highly customisable to allow querying of many different data sources of " +
                "different types.\n" +
                "Queried data can be displayed in tabular form, visualised using interactive charts/graphs or " +
                "render as HTML.\n" +
                "\n" +
                "The Dashboard Doc can either be used for ad-hoc querying/visualising of data, to construct " +
                "a dashboard for others to view or to just view an already constructed dashboard.\n" +
                "Dashboards can be parameterised so that all queries on the dashboard are displaying data for the " +
                "same user, for example.\n" +
                "For ad-hoc querying of data from one data source, you are recommended to use a " +
                "[Query]({{< relref \"#query\" >}}) instead."
)
@JsonPropertyOrder({
        "uuid",
        "name",
        "uniqueName",
        "version",
        "createTimeMs",
        "updateTimeMs",
        "createUser",
        "updateUser",
        "description",
        "dashboardConfig"})
@JsonInclude(Include.NON_NULL)
public class DashboardDoc extends AbstractDoc {

    public static final String DOCUMENT_TYPE = "Dashboard";
    public static final SvgImage ICON = SvgImage.DOCUMENT_DASHBOARD;

    @JsonProperty
    private String description;
    @JsonProperty
    private DashboardConfig dashboardConfig;

    public DashboardDoc() {
    }

    @JsonCreator
    public DashboardDoc(@JsonProperty("uuid") final String uuid,
                        @JsonProperty("name") final String name,
                        @JsonProperty("uniqueName") final String uniqueName,
                        @JsonProperty("version") final String version,
                        @JsonProperty("createTimeMs") final Long createTimeMs,
                        @JsonProperty("updateTimeMs") final Long updateTimeMs,
                        @JsonProperty("createUser") final String createUser,
                        @JsonProperty("updateUser") final String updateUser,
                        @JsonProperty("description") final String description,
                        @JsonProperty("dashboardConfig") final DashboardConfig dashboardConfig) {
        super(uuid, name, uniqueName, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.dashboardConfig = dashboardConfig;
    }

    @JsonIgnore
    @Override
    public final String getType() {
        return DOCUMENT_TYPE;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public DashboardConfig getDashboardConfig() {
        return dashboardConfig;
    }

    public void setDashboardConfig(final DashboardConfig dashboardConfig) {
        this.dashboardConfig = dashboardConfig;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final DashboardDoc that = (DashboardDoc) o;
        return Objects.equals(dashboardConfig, that.dashboardConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), dashboardConfig);
    }
}
