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
import stroom.docs.shared.Description;
import stroom.docstore.shared.AbstractDoc;
import stroom.query.api.v2.ExpressionOperator;
import stroom.svg.shared.SvgImage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@Description(
        "A view is an abstraction over a data source (such as a " +
                "[Lucene Indexe]({{< relref \"#lucene-index\" >}})) and optionally an " +
                "[extraction pipeline]({{< relref \"docs/user-guide/pipelines/recipies#search-extraction\" >}}).\n" +
                "Views provide a much simpler way for users to query data as the user can simply query against " +
                "the View without any knowledge of the underlying data source or extraction of that data.")
@JsonPropertyOrder({
        "type",
        "uuid",
        "name",
        "uniqueName",
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
public class ViewDoc extends AbstractDoc {

    public static final String DOCUMENT_TYPE = "View";
    public static final SvgImage ICON = SvgImage.DOCUMENT_VIEW;

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
    public ViewDoc(@JsonProperty("uuid") final String uuid,
                   @JsonProperty("name") final String name,
                   @JsonProperty("uniqueName") final String uniqueName,
                   @JsonProperty("version") final String version,
                   @JsonProperty("createTimeMs") final Long createTimeMs,
                   @JsonProperty("updateTimeMs") final Long updateTimeMs,
                   @JsonProperty("createUser") final String createUser,
                   @JsonProperty("updateUser") final String updateUser,
                   @JsonProperty("description") final String description,
                   @JsonProperty("dataSource") DocRef dataSource,
                   @JsonProperty("filter") ExpressionOperator filter,
                   @JsonProperty("pipeline") final DocRef pipeline) {
        super(uuid, name, uniqueName, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.dataSource = dataSource;
        this.filter = filter;
        this.pipeline = pipeline;
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
        return Objects.equals(description, viewDoc.description) &&
                Objects.equals(dataSource, viewDoc.dataSource) &&
                Objects.equals(filter, viewDoc.filter) &&
                Objects.equals(pipeline, viewDoc.pipeline);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, dataSource, filter, pipeline);
    }
}
