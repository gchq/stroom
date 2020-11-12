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

package stroom.query.api.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import stroom.docref.DocRef;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"queryId", "fields", "extractValues", "extractionPipeline", "maxResults",
        "showDetail"})
@JsonInclude(Include.NON_NULL)
@XmlType(
        name = "TableSettings",
        propOrder = {"queryId", "fields", "extractValues", "extractionPipeline", "maxResults", "showDetail"})
@XmlAccessorType(XmlAccessType.FIELD)
@ApiModel(description = "An object to describe how the query results should be returned, including which fields " +
        "should be included and what sorting, grouping, filtering, limiting, etc. should be applied")
public final class TableSettings implements Serializable {
    private static final long serialVersionUID = -2530827581046882396L;

    @XmlElement
    @ApiModelProperty(
            value = "TODO",
            required = true)
    @JsonProperty
    private String queryId;

    @XmlElementWrapper(name = "fields")
    @XmlElement(name = "field")
    @ApiModelProperty(required = true)
    @JsonProperty
    private List<Field> fields;

    @XmlElement
    @ApiModelProperty(
            value = "TODO")
    @JsonProperty
    private Boolean extractValues;

    @XmlElement
    @JsonProperty
    private DocRef extractionPipeline;

    @XmlElementWrapper(name = "maxResults")
    @XmlElement(name = "val")
    @ApiModelProperty(
            value = "Defines the maximum number of results to return at each grouping level, e.g. '1000,10,1' means " +
                    "1000 results at group level 0, 10 at level 1 and 1 at level 2. In the absence of this field " +
                    "system defaults will apply",
            example = "1000,10,1")
    @JsonProperty
    private List<Integer> maxResults;

    @XmlElement
    @ApiModelProperty(
            value = "When grouping is used a value of true indicates that the results will include the full detail of " +
                    "any results aggregated into a group as well as their aggregates. A value of false will only " +
                    "include the aggregated values for each group. Defaults to false.")
    @JsonProperty
    private Boolean showDetail;

    public TableSettings() {
    }

    @JsonCreator
    public TableSettings(@JsonProperty("queryId") final String queryId,
                         @JsonProperty("fields") final List<Field> fields,
                         @JsonProperty("extractValues") final Boolean extractValues,
                         @JsonProperty("extractionPipeline") final DocRef extractionPipeline,
                         @JsonProperty("maxResults") final List<Integer> maxResults,
                         @JsonProperty("showDetail") final Boolean showDetail) {
        this.queryId = queryId;
        this.fields = fields;
        this.extractValues = extractValues;
        this.extractionPipeline = extractionPipeline;
        this.maxResults = maxResults;
        this.showDetail = showDetail;
    }

    public String getQueryId() {
        return queryId;
    }

    public void setQueryId(final String queryId) {
        this.queryId = queryId;
    }

    public List<Field> getFields() {
        return fields;
    }

    public void setFields(final List<Field> fields) {
        this.fields = fields;
    }

    public Boolean getExtractValues() {
        return extractValues;
    }

    public void setExtractValues(final Boolean extractValues) {
        this.extractValues = extractValues;
    }

    public boolean extractValues() {
        if (extractValues == null) {
            return false;
        }
        return extractValues;
    }

    public DocRef getExtractionPipeline() {
        return extractionPipeline;
    }

    public void setExtractionPipeline(final DocRef extractionPipeline) {
        this.extractionPipeline = extractionPipeline;
    }

    public List<Integer> getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(final List<Integer> maxResults) {
        this.maxResults = maxResults;
    }

    public Boolean getShowDetail() {
        return showDetail;
    }

    public void setShowDetail(final Boolean showDetail) {
        this.showDetail = showDetail;
    }

    public boolean showDetail() {
        if (showDetail == null) {
            return false;
        }
        return showDetail;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TableSettings that = (TableSettings) o;
        return Objects.equals(queryId, that.queryId) &&
                Objects.equals(fields, that.fields) &&
                Objects.equals(extractValues, that.extractValues) &&
                Objects.equals(extractionPipeline, that.extractionPipeline) &&
                Objects.equals(maxResults, that.maxResults) &&
                Objects.equals(showDetail, that.showDetail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(queryId, fields, extractValues, extractionPipeline, maxResults, showDetail);
    }

    @Override
    public String toString() {
        return "TableSettings{" +
                "queryId='" + queryId + '\'' +
                ", fields=" + fields +
                ", extractValues=" + extractValues +
                ", extractionPipeline=" + extractionPipeline +
                ", maxResults=" + maxResults +
                ", showDetail=" + showDetail +
                '}';
    }

    /**
     * Builder for constructing a {@link TableSettings tableSettings}
     */
    public static class Builder {
        private String queryId;
        private final List<Field> fields = new ArrayList<>();
        private Boolean extractValues;
        private DocRef extractionPipeline;
        private Boolean showDetail;

        private final List<Integer> maxResults = new ArrayList<>();

        /**
         * @param value The ID for the query that wants these results
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder queryId(final String value) {
            this.queryId = value;
            return this;
        }

        /**
         * @param values Add expected fields to the output table
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder addFields(final Field... values) {
            return addFields(Arrays.asList(values));
        }

        /**
         * Convenience function for adding multiple fields that are already in a collection.
         *
         * @param values The fields to add
         * @return this builder, with the fields added.
         */
        public Builder addFields(final Collection<Field> values) {
            this.fields.addAll(values);
            return this;
        }

        /**
         * @param values The max result value
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder addMaxResults(final Integer... values) {
            return addMaxResults(Arrays.asList(values));
        }

        /**
         * Add a collection of max result values
         *
         * @param values The list of max result values
         * @return this builder
         */
        public Builder addMaxResults(final Collection<Integer> values) {
            this.maxResults.addAll(values);
            return this;
        }

        /**
         * @param value TODO - unknown purpose
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder extractValues(final Boolean value) {
            this.extractValues = value;
            return this;
        }

        /**
         * @param value The reference to the extraction pipeline that will be used on the results
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder extractionPipeline(final DocRef value) {
            this.extractionPipeline = value;
            return this;
        }

        /**
         * Shortcut function for creating the extractionPipeline {@link DocRef} in one go
         *
         * @param type The type of the extractionPipeline
         * @param uuid The UUID of the extractionPipeline
         * @param name The name of the extractionPipeline
         * @return this builder, with the completed extractionPipeline added.
         */
        public Builder extractionPipeline(final String type,
                                          final String uuid,
                                          final String name) {
            return this.extractionPipeline(new DocRef.Builder().type(type).uuid(uuid).name(name).build());
        }

        /**
         * @param value When grouping is used a value of true indicates that the results will include
         *              the full detail of any results aggregated into a group as well as their aggregates.
         *              A value of false will only include the aggregated values for each group. Defaults to false.
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder showDetail(final Boolean value) {
            this.showDetail = value;
            return this;
        }

        public TableSettings build() {
            return new TableSettings(queryId, fields, extractValues, extractionPipeline, maxResults, showDetail);
        }
    }
}