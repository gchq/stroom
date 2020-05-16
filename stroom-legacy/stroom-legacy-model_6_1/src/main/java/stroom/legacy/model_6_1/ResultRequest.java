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

package stroom.legacy.model_6_1;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@XmlType(name = "ResultRequest", propOrder = {"componentId", "mappings", "requestedRange", "openGroups", "resultStyle", "fetch"})
@XmlAccessorType(XmlAccessType.FIELD)
@ApiModel(description = "A definition for how to return the raw results of the query in the SearchResponse, " +
        "e.g. sorted, grouped, limited, etc.")
public final class ResultRequest implements Serializable {
    private static final long serialVersionUID = -7455554742243923562L;

    @XmlElement
    @ApiModelProperty(
            value = "The ID of the component that will receive the results corresponding to this ResultRequest",
            required = true)
    private String componentId;

    @XmlElementWrapper(name = "mappings")
    @XmlElement(name = "mappings")
    @ApiModelProperty(required = true)
    private List<TableSettings> mappings;

    @XmlElement
    @ApiModelProperty(required = true)
    private OffsetRange requestedRange;

    @XmlElementWrapper(name = "openGroups")
    @XmlElement(name = "key")
    //TODO complete documentation
    @ApiModelProperty(
            value = "TODO",
            required = true)
    private List<String> openGroups;

    @XmlElement
    @ApiModelProperty(
            value = "The style of results required. FLAT will provide a FlatResult object, while TABLE will " +
                    "provide a TableResult object",
            required = true)
    private ResultStyle resultStyle;

    @XmlElement
    @ApiModelProperty(
            value = "The fetch mode for the query. NONE means fetch no data, ALL means fetch all known results, " +
                    "CHANGES means fetch only those records not see in previous requests",
            required = false)
    private Fetch fetch;

    private ResultRequest() {
    }

    public ResultRequest(final String componentId) {
        this.componentId = componentId;
    }

    public ResultRequest(final String componentId, final TableSettings mappings) {
        this(componentId, Collections.singletonList(mappings), null);
    }

    public ResultRequest(final String componentId, final TableSettings mappings, final OffsetRange requestedRange) {
        this(componentId, Collections.singletonList(mappings), requestedRange);
    }

    public ResultRequest(final String componentId, final List<TableSettings> mappings, final OffsetRange requestedRange) {
        this.componentId = componentId;
        this.mappings = mappings;
        this.requestedRange = requestedRange;
        this.resultStyle = ResultStyle.FLAT;
    }

    public ResultRequest(final String componentId,
                         final List<TableSettings> mappings,
                         final OffsetRange requestedRange,
                         final List<String> openGroups,
                         final ResultStyle resultStyle,
                         final Fetch fetch) {
        this.componentId = componentId;
        this.mappings = mappings;
        this.requestedRange = requestedRange;
        this.openGroups = openGroups;
        this.resultStyle = resultStyle;
        this.fetch = fetch;
    }

    public String getComponentId() {
        return componentId;
    }

    public List<TableSettings> getMappings() {
        return mappings;
    }

    public OffsetRange getRequestedRange() {
        return requestedRange;
    }

    public List<String> getOpenGroups() {
        return openGroups;
    }

    public ResultStyle getResultStyle() {
        return resultStyle;
    }

    /**
     * The fetch type determines if the request actually wants data returned or if it only wants data if the data has
     * changed since the last request was made.
     * @return The fetch type.
     */
    public Fetch getFetch() {
        return fetch;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final ResultRequest that = (ResultRequest) o;

        if (componentId != null ? !componentId.equals(that.componentId) : that.componentId != null) return false;
        if (mappings != null ? !mappings.equals(that.mappings) : that.mappings != null)
            return false;
        if (requestedRange != null ? !requestedRange.equals(that.requestedRange) : that.requestedRange != null)
            return false;
        if (openGroups != null ? !openGroups.equals(that.openGroups) : that.openGroups != null) return false;
        if (resultStyle != that.resultStyle) return false;
        return fetch != null ? fetch.equals(that.fetch) : that.fetch == null;
    }

    @Override
    public int hashCode() {
        int result = componentId != null ? componentId.hashCode() : 0;
        result = 31 * result + (mappings != null ? mappings.hashCode() : 0);
        result = 31 * result + (requestedRange != null ? requestedRange.hashCode() : 0);
        result = 31 * result + (openGroups != null ? openGroups.hashCode() : 0);
        result = 31 * result + (resultStyle != null ? resultStyle.hashCode() : 0);
        result = 31 * result + (fetch != null ? fetch.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ResultRequest{" +
                "componentId='" + componentId + '\'' +
                ", mappings=" + mappings +
                ", requestedRange=" + requestedRange +
                ", openGroups=" + openGroups +
                ", resultStyle=" + resultStyle +
                ", fetch=" + fetch +
                '}';
    }

    public enum ResultStyle {
        FLAT,
        TABLE
    }

    public enum Fetch {
        NONE,
        CHANGES,
        ALL
    }

    /**
     * Builder for constructing a {@link ResultRequest}
     */
    public static class Builder {
        private String componentId;

        private final List<TableSettings> mappings = new ArrayList<>();

        private OffsetRange requestedRange;

        private final List<String> openGroups = new ArrayList<>();

        private ResultStyle resultStyle;

        private Fetch fetch;

        /**
         * @param value The ID of the component that will receive the results corresponding to this ResultRequest
         *
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder componentId(final String value) {
            this.componentId = value;
            return this;
        }

        /**
         * @param value Set the requested range of the results.
         *
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder requestedRange(final OffsetRange value) {
            this.requestedRange = value;
            return this;
        }

        /**
         * @param values Adding a set of TableSettings which are used to map the raw results to the output
         *
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder addMappings(final TableSettings... values) {
            this.mappings.addAll(Arrays.asList(values));
            return this;
        }

        /**
         * @param values TODO
         *
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder addOpenGroups(final String...values) {
            this.openGroups.addAll(Arrays.asList(values));
            return this;
        }

        /**
         * @param value The style of results required.
         *              FLAT will provide a FlatResult object,
         *              while TABLE will provide a TableResult object
         *
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder resultStyle(final ResultStyle value) {
            this.resultStyle = value;
            return this;
        }

        /**
         * @param value The fetch mode for the query.
         *              NONE means fetch no data,
         *              ALL means fetch all known results,
         *              CHANGES means fetch only those records not see in previous requests
         *
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder fetch(final Fetch value) {
            this.fetch = value;
            return this;
        }

        public ResultRequest build() {
            return new ResultRequest(componentId, mappings, requestedRange, openGroups, resultStyle, fetch);
        }
    }
}