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

package stroom.query.api;

import stroom.docref.DocRef;
import stroom.docref.HasDisplayValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class SearchRequestSource {

    @JsonProperty
    private final SourceType sourceType;
    @JsonProperty
    private final DocRef ownerDocRef;
    @JsonProperty
    private final String componentId;
    @JsonProperty
    private final String componentName;

    @JsonCreator
    public SearchRequestSource(
            @JsonProperty("sourceType") final SourceType sourceType,
            @JsonProperty("ownerDocRef") final DocRef ownerDocRef,
            @JsonProperty("componentId") final String componentId,
            @JsonProperty("componentName") final String componentName) {
        this.sourceType = sourceType;
        this.ownerDocRef = ownerDocRef;
        this.componentId = componentId;
        this.componentName = componentName;
    }

    public static SearchRequestSource createBasic() {
        return builder().sourceType(SourceType.DASHBOARD_UI).build();
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public DocRef getOwnerDocRef() {
        return ownerDocRef;
    }

    public String getComponentId() {
        return componentId;
    }

    public String getComponentName() {
        return componentName;
    }

    @Override
    public String toString() {
        return "SearchRequestSource{" +
                "sourceType=" + sourceType +
                ", ownerDocRef='" + ownerDocRef + '\'' +
                ", componentId='" + componentId + '\'' +
                ", componentName='" + componentName  + '\'' +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SearchRequestSource that = (SearchRequestSource) o;
        return sourceType == that.sourceType &&
               Objects.equals(ownerDocRef, that.ownerDocRef) &&
               Objects.equals(componentId, that.componentId) &&
               Objects.equals(componentName, that.componentName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceType, ownerDocRef, componentId, componentName);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private SourceType sourceType;
        private DocRef ownerDocRef;
        private String componentId;
        private String componentName;

        private Builder() {
        }

        private Builder(final SearchRequestSource searchRequestSource) {
            this.sourceType = searchRequestSource.sourceType;
            this.ownerDocRef = searchRequestSource.ownerDocRef;
            this.componentId = searchRequestSource.componentId;
            this.componentName = searchRequestSource.componentName;
        }

        public Builder sourceType(final SourceType sourceType) {
            this.sourceType = sourceType;
            return this;
        }

        public Builder ownerDocRef(final DocRef ownerDocRef) {
            this.ownerDocRef = ownerDocRef;
            return this;
        }

        public Builder componentId(final String componentId) {
            this.componentId = componentId;
            return this;
        }

        public Builder componentName(final String componentName) {
            this.componentName = componentName;
            return this;
        }

        public SearchRequestSource build() {
            return new SearchRequestSource(
                    sourceType,
                    ownerDocRef,
                    componentId,
                    componentName);
        }
    }

    public enum SourceType implements HasDisplayValue {
        TABLE_BUILDER_ANALYTIC("Table Builder Analytic", true, true, true),
        SCHEDULED_QUERY_ANALYTIC("Scheduled Query Analytic", false, false, false),
        DASHBOARD_UI("Dashboard UI", false, false, false),
        QUERY_UI("Query UI", false, false, false),
        API("API Request", false, false, false),
        BATCH_SEARCH("Batch Search", false, false, false);

        private final boolean requireTimeValue;
        private final boolean requireStreamIdValue;
        private final boolean requireEventIdValue;
        private final String displayValue;

        SourceType(final String displayValue,
                   final boolean requireTimeValue,
                   final boolean requireStreamIdValue,
                   final boolean requireEventIdValue) {
            this.displayValue = displayValue;
            this.requireTimeValue = requireTimeValue;
            this.requireStreamIdValue = requireStreamIdValue;
            this.requireEventIdValue = requireEventIdValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }

        public boolean isRequireTimeValue() {
            return requireTimeValue;
        }

        public boolean isRequireStreamIdValue() {
            return requireStreamIdValue;
        }

        public boolean isRequireEventIdValue() {
            return requireEventIdValue;
        }
    }
}
