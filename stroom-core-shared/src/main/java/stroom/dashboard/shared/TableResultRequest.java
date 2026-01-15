/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.query.api.GroupSelection;
import stroom.query.api.OffsetRange;
import stroom.query.api.ResultRequest.Fetch;
import stroom.query.api.TableSettings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;
import java.util.Set;

@JsonPropertyOrder({
        "componentId",
        "fetch",
        "tableName",
        "tableSettings",
        "requestedRange",
        "openGroups",
        "groupSelection"})
@JsonInclude(Include.NON_NULL)
public final class TableResultRequest extends ComponentResultRequest {

    @JsonProperty
    private final String tableName;
    @JsonProperty
    private final TableSettings tableSettings;
    @JsonProperty
    private final OffsetRange requestedRange;
    /**
     * @deprecated Use {@link GroupSelection#openGroups} instead.
     */
    @JsonProperty
    @Deprecated
    private final Set<String> openGroups;
    @JsonProperty
    private final GroupSelection groupSelection;

    @JsonCreator
    public TableResultRequest(@JsonProperty("componentId") final String componentId,
                              @JsonProperty("fetch") final Fetch fetch,
                              @JsonProperty("tableName") final String tableName,
                              @JsonProperty("tableSettings") final TableSettings tableSettings,
                              @JsonProperty("requestedRange") final OffsetRange requestedRange,
                              @JsonProperty("openGroups") final Set<String> openGroups,
                              @JsonProperty("groupSelection") final GroupSelection groupSelection) {
        super(componentId, fetch);
        this.tableName = tableName;
        this.tableSettings = tableSettings;
        this.requestedRange = requestedRange;
        this.openGroups = openGroups;
        this.groupSelection = groupSelection == null ?
                GroupSelection.builder().openGroups(openGroups).build() : groupSelection;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getTableName() {
        return tableName;
    }

    public TableSettings getTableSettings() {
        return tableSettings;
    }

    public OffsetRange getRequestedRange() {
        return requestedRange;
    }

    public Set<String> getOpenGroups() {
        return openGroups;
    }

    public GroupSelection getGroupSelection() {
        return groupSelection;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TableResultRequest that = (TableResultRequest) o;
        return Objects.equals(tableName, that.tableName) &&
               Objects.equals(tableSettings, that.tableSettings) &&
               Objects.equals(requestedRange, that.requestedRange) &&
               Objects.equals(openGroups, that.openGroups) &&
               Objects.equals(groupSelection, that.groupSelection);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                tableName,
                tableSettings,
                requestedRange,
                openGroups,
                groupSelection);
    }

    @Override
    public String toString() {
        return "TableResultRequest{" +
                "tableName='" + tableName + '\'' +
                ",tableSettings=" + tableSettings +
                ", requestedRange=" + requestedRange +
                ", openGroups=" + openGroups +
                ", groupSelection=" + groupSelection +
                '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private String componentId;
        private Fetch fetch;
        private String tableName;
        private TableSettings tableSettings;
        private OffsetRange requestedRange = OffsetRange.ZERO_100;
        private Set<String> openGroups;
        private GroupSelection groupSelection = new GroupSelection();

        private Builder() {
        }

        private Builder(final TableResultRequest tableResultRequest) {
            this.componentId = tableResultRequest.getComponentId();
            this.fetch = tableResultRequest.getFetch();
            this.tableName = tableResultRequest.tableName;
            this.tableSettings = tableResultRequest.tableSettings;
            this.requestedRange = tableResultRequest.requestedRange;
            this.openGroups = tableResultRequest.openGroups;
            this.groupSelection = tableResultRequest.groupSelection;
        }

        public Builder componentId(final String componentId) {
            this.componentId = componentId;
            return this;
        }

        public Builder fetch(final Fetch fetch) {
            this.fetch = fetch;
            return this;
        }

        public Builder tableName(final String tableName) {
            this.tableName = tableName;
            return this;
        }

        public Builder tableSettings(final TableSettings tableSettings) {
            this.tableSettings = tableSettings;
            return this;
        }

        public Builder requestedRange(final OffsetRange requestedRange) {
            this.requestedRange = requestedRange;
            return this;
        }

        public Builder openGroups(final Set<String> openGroups) {
            this.openGroups = openGroups;
            return this;
        }

        public Builder groupSelection(final GroupSelection groupSelection) {
            this.groupSelection = groupSelection;
            return this;
        }

        public TableResultRequest build() {
            return new TableResultRequest(
                    componentId,
                    fetch,
                    tableName,
                    tableSettings,
                    requestedRange,
                    openGroups,
                    groupSelection);
        }
    }
}
