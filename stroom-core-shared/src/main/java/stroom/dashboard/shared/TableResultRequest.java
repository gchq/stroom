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

import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.ResultRequest.Fetch;
import stroom.query.api.v2.TableSettings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@JsonPropertyOrder({
        "componentId",
        "fetch",
        "tableName",
        "tableSettings",
        "requestedRange",
        "openGroups"})
@JsonInclude(Include.NON_NULL)
public class TableResultRequest extends ComponentResultRequest {

    @JsonProperty
    private final String tableName;
    @JsonProperty
    private final TableSettings tableSettings;
    @JsonProperty
    private final OffsetRange requestedRange;
    @JsonProperty
    private final Set<String> openGroups;

    @JsonCreator
    public TableResultRequest(@JsonProperty("componentId") final String componentId,
                              @JsonProperty("fetch") final Fetch fetch,
                              @JsonProperty("tableName") final String tableName,
                              @JsonProperty("tableSettings") final TableSettings tableSettings,
                              @JsonProperty("requestedRange") final OffsetRange requestedRange,
                              @JsonProperty("openGroups") final Set<String> openGroups) {
        super(componentId, fetch);
        this.tableName = tableName;
        this.tableSettings = tableSettings;
        this.requestedRange = requestedRange;
        this.openGroups = openGroups;
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

    public boolean isGroupOpen(final String group) {
        return openGroups != null && openGroups.contains(group);
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
                Objects.equals(openGroups, that.openGroups);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                tableName,
                tableSettings,
                requestedRange,
                openGroups);
    }

    @Override
    public String toString() {
        return "TableResultRequest{" +
                "tableName='" + tableName + '\'' +
                ",tableSettings=" + tableSettings +
                ", requestedRange=" + requestedRange +
                ", openGroups=" + openGroups +
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
        private OffsetRange requestedRange = new OffsetRange(0, 100);
        private Set<String> openGroups;

        private Builder() {
        }

        private Builder(final TableResultRequest tableResultRequest) {
            this.componentId = tableResultRequest.getComponentId();
            this.fetch = tableResultRequest.getFetch();
            this.tableName = tableResultRequest.tableName;
            this.tableSettings = tableResultRequest.tableSettings;
            this.requestedRange = tableResultRequest.requestedRange;
            this.openGroups = tableResultRequest.openGroups;
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

        public Builder openGroup(final String group, final boolean open) {
            if (openGroups == null) {
                openGroups = new HashSet<>();
            }

            if (open) {
                openGroups.add(group);
            } else {
                openGroups.remove(group);
            }

            return this;
        }

        public TableResultRequest build() {
            return new TableResultRequest(
                    componentId,
                    fetch,
                    tableName,
                    tableSettings,
                    requestedRange,
                    openGroups);
        }
    }
}
