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
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.query.api.ExpressionOperator;

import com.fasterxml.jackson.annotation.JsonCreator;
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

    public static final String TYPE = "View";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.VIEW_DOCUMENT_TYPE;

    @JsonProperty
    private String description;
    @JsonProperty
    private DocRef dataSource;
    @JsonProperty
    private ExpressionOperator filter;
    @JsonProperty
    private DocRef pipeline;

    @JsonCreator
    public ViewDoc(@JsonProperty("uuid") final String uuid,
                   @JsonProperty("name") final String name,
                   @JsonProperty("version") final String version,
                   @JsonProperty("createTimeMs") final Long createTimeMs,
                   @JsonProperty("updateTimeMs") final Long updateTimeMs,
                   @JsonProperty("createUser") final String createUser,
                   @JsonProperty("updateUser") final String updateUser,
                   @JsonProperty("description") final String description,
                   @JsonProperty("dataSource") final DocRef dataSource,
                   @JsonProperty("filter") final ExpressionOperator filter,
                   @JsonProperty("pipeline") final DocRef pipeline) {
        super(TYPE, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.dataSource = dataSource;
        this.filter = filter;
        this.pipeline = pipeline;
    }

    /**
     * @return A new {@link DocRef} for this document's type with the supplied uuid.
     */
    public static DocRef getDocRef(final String uuid) {
        return DocRef.builder(TYPE)
                .uuid(uuid)
                .build();
    }

    /**
     * @return A new builder for creating a {@link DocRef} for this document's type.
     */
    public static DocRef.TypedBuilder buildDocRef() {
        return DocRef.builder(TYPE);
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

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends AbstractDoc.AbstractBuilder<ViewDoc, ViewDoc.Builder> {

        private String description;
        private DocRef dataSource;
        private ExpressionOperator filter;
        private DocRef pipeline;

        private Builder() {
        }

        private Builder(final ViewDoc viewDoc) {
            super(viewDoc);
            this.description = viewDoc.description;
            this.dataSource = viewDoc.dataSource;
            this.filter = viewDoc.filter;
            this.pipeline = viewDoc.pipeline;
        }

        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        public Builder dataSource(final DocRef dataSource) {
            this.dataSource = dataSource;
            return self();
        }

        public Builder filter(final ExpressionOperator filter) {
            this.filter = filter;
            return self();
        }

        public Builder pipeline(final DocRef pipeline) {
            this.pipeline = pipeline;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public ViewDoc build() {
            return new ViewDoc(
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    description,
                    dataSource,
                    filter,
                    pipeline);
        }
    }
}
