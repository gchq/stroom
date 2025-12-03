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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({
        "groupKey",
        "annotationId",
        "values",
        "depth",
        "matchingRule"
})
@JsonInclude(Include.NON_NULL)
@Schema(description = "A row of data in a result set")
public final class Row {

    @JsonProperty
    private final String groupKey;

    @JsonProperty
    private final Long annotationId;

    @Schema(description = "The value for this row of data. The values in the list are in the same order as the " +
                          "fields in the ResultRequest"
    )
    @JsonProperty
    private final List<String> values;

    @Schema(description = "The grouping depth, where 0 is the top level of grouping, or where there is no grouping",
            example = "0")
    @JsonProperty
    private final Integer depth;

    @JsonProperty
    @JsonPropertyDescription("The id of a matching conditional formatting rule or null if none matched.")
    private final String matchingRule;

    @JsonCreator
    public Row(@JsonProperty("groupKey") final String groupKey,
               @JsonProperty("annotationId") final Long annotationId,
               @JsonProperty("values") final List<String> values,
               @JsonProperty("depth") final Integer depth,
               @JsonProperty("matchingRule") final String matchingRule) {
        this.groupKey = groupKey;
        this.annotationId = annotationId;
        this.values = values;
        this.depth = depth;
        this.matchingRule = matchingRule;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public Long getAnnotationId() {
        return annotationId;
    }

    public List<String> getValues() {
        return values;
    }

    public Integer getDepth() {
        return depth;
    }

    public String getMatchingRule() {
        return matchingRule;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Row row = (Row) o;
        return Objects.equals(groupKey, row.groupKey) &&
               Objects.equals(annotationId, row.annotationId) &&
               Objects.equals(values, row.values) &&
               Objects.equals(depth, row.depth) &&
               Objects.equals(matchingRule, row.matchingRule);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupKey, annotationId, values, depth, matchingRule);
    }

    @Override
    public String toString() {
        return "Row{" +
               "groupKey='" + groupKey + '\'' +
               ", annotationId=" + annotationId +
               ", values=" + values +
               ", depth=" + depth +
               ", matchingRule='" + matchingRule + '\'' +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    /**
     * Builder for constructing a {@link Row}
     */
    public static final class Builder {

        private String groupKey;
        private Long annotationId;
        private List<String> values;
        private Integer depth = 0;
        private String matchingRule;

        private Builder() {
        }

        private Builder(final Row row) {
            groupKey = row.groupKey;
            annotationId = row.annotationId;
            values = row.values;
            depth = row.depth;
            matchingRule = row.matchingRule;
        }

        /**
         * @param value TODO
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder groupKey(final String value) {
            this.groupKey = value;
            return this;
        }

        public Builder annotationId(final Long annotationId) {
            this.annotationId = annotationId;
            return this;
        }

        /**
         * @param values The value for this row of data.
         *               The values in the list are in the same order as the fields in the ResultRequest
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder values(final List<String> values) {
            this.values = values;
            return this;
        }

        /**
         * @param value The grouping depth, where 0 is the top level of grouping, or where there is no grouping
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder depth(final Integer value) {
            this.depth = value;
            return this;
        }

        /**
         * The id of a matching conditional formatting rule.
         *
         * @param matchingRule The id of a matching conditional formatting rule.
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder matchingRule(final String matchingRule) {
            this.matchingRule = matchingRule;
            return this;
        }

        public Row build() {
            return new Row(groupKey, annotationId, values, depth, matchingRule);
        }
    }
}
