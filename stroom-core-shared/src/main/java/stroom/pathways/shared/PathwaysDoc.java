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

package stroom.pathways.shared;

import stroom.docref.DocRef;
import stroom.docstore.shared.Doc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.pathways.shared.pathway.Pathway;
import stroom.util.shared.time.SimpleDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
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
        "pathways"})
@JsonInclude(Include.NON_NULL)
public class PathwaysDoc extends Doc {

    public static final String TYPE = "Pathways";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.PATHWAYS_DOCUMENT_TYPE;

    @JsonProperty
    private String description;
    @JsonProperty
    private SimpleDuration temporalOrderingTolerance;
    @JsonProperty
    private List<Pathway> pathways;

    public PathwaysDoc() {

    }

    @JsonCreator
    public PathwaysDoc(@JsonProperty("type") final String type,
                       @JsonProperty("uuid") final String uuid,
                       @JsonProperty("name") final String name,
                       @JsonProperty("version") final String version,
                       @JsonProperty("createTimeMs") final Long createTimeMs,
                       @JsonProperty("updateTimeMs") final Long updateTimeMs,
                       @JsonProperty("createUser") final String createUser,
                       @JsonProperty("updateUser") final String updateUser,
                       @JsonProperty("description") final String description,
                       @JsonProperty("temporalOrderingTolerance") final SimpleDuration temporalOrderingTolerance,
                       @JsonProperty("pathways") final List<Pathway> pathways) {
        super(type, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.temporalOrderingTolerance = temporalOrderingTolerance;
        this.pathways = pathways;
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

    public SimpleDuration getTemporalOrderingTolerance() {
        return temporalOrderingTolerance;
    }

    public void setTemporalOrderingTolerance(final SimpleDuration temporalOrderingTolerance) {
        this.temporalOrderingTolerance = temporalOrderingTolerance;
    }

    public List<Pathway> getPathways() {
        return pathways;
    }

    public void setPathways(final List<Pathway> pathways) {
        this.pathways = pathways;
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
        final PathwaysDoc that = (PathwaysDoc) o;
        return Objects.equals(description, that.description) &&
               Objects.equals(temporalOrderingTolerance, that.temporalOrderingTolerance) &&
               Objects.equals(pathways, that.pathways);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, temporalOrderingTolerance, pathways);
    }
}
