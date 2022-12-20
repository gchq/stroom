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

package stroom.view.shared;

import stroom.docref.DocRef;
import stroom.docstore.shared.Doc;
import stroom.query.api.v2.ExpressionOperator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "type",
        "uuid",
        "name",
        "version",
        "createTimeMs",
        "updateTimeMs",
        "createUser",
        "updateUser",
        "description",
        "dataSource",
        "filter",
        "pipeline"})
@JsonInclude(Include.NON_NULL)
public class ViewDoc extends Doc {

    public static final String DOCUMENT_TYPE = "View";

    @JsonProperty
    private String description;
    @JsonProperty
    private DocRef dataSource;
    @JsonProperty
    private ExpressionOperator filter;
    @JsonProperty
    private DocRef pipeline;

    public ViewDoc() {
    }

    @JsonCreator
    public ViewDoc(@JsonProperty("type") final String type,
                   @JsonProperty("uuid") final String uuid,
                   @JsonProperty("name") final String name,
                   @JsonProperty("version") final String version,
                   @JsonProperty("createTimeMs") final Long createTimeMs,
                   @JsonProperty("updateTimeMs") final Long updateTimeMs,
                   @JsonProperty("createUser") final String createUser,
                   @JsonProperty("updateUser") final String updateUser,
                   @JsonProperty("description") final String description,
                   @JsonProperty("dataSource") DocRef dataSource,
                   @JsonProperty("filter") ExpressionOperator filter,
                   @JsonProperty("pipeline") final DocRef pipeline) {
        super(type, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.dataSource = dataSource;
        this.filter = filter;
        this.pipeline = pipeline;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public DocRef getDataSource() {
        return dataSource;
    }

    public void setDataSource(final DocRef dataSource) {
        this.dataSource = dataSource;
    }

    public ExpressionOperator getFilter() {
        return filter;
    }

    public void setFilter(final ExpressionOperator filter) {
        this.filter = filter;
    }

    public DocRef getPipeline() {
        return pipeline;
    }

    public void setPipeline(final DocRef pipeline) {
        this.pipeline = pipeline;
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
        final ViewDoc viewDoc = (ViewDoc) o;
        return Objects.equals(description, viewDoc.description) && Objects.equals(dataSource,
                viewDoc.dataSource) && Objects.equals(filter,
                viewDoc.filter) && Objects.equals(pipeline, viewDoc.pipeline);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, dataSource, filter, pipeline);
    }
}
