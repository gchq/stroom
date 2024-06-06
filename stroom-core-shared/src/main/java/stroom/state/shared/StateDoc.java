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

package stroom.state.shared;

import stroom.docref.DocRef;
import stroom.docs.shared.Description;
import stroom.docstore.shared.Doc;
import stroom.svg.shared.SvgImage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@Description("Defines a place to store state")
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
        "scyllaDbRef"
})
@JsonInclude(Include.NON_NULL)
public class StateDoc extends Doc {

    public static final String DOCUMENT_TYPE = "StateStore";
    public static final SvgImage ICON = SvgImage.DOCUMENT_STATE_STORE;

    /**
     * Reference to the `scyllaDb` containing common connection properties
     */
    @JsonProperty
    private DocRef scyllaDbRef;

    @JsonProperty
    private String description;

    public StateDoc() {
    }

    @JsonCreator
    public StateDoc(
            @JsonProperty("type") final String type,
            @JsonProperty("uuid") final String uuid,
            @JsonProperty("name") final String name,
            @JsonProperty("version") final String version,
            @JsonProperty("createTimeMs") final Long createTimeMs,
            @JsonProperty("updateTimeMs") final Long updateTimeMs,
            @JsonProperty("createUser") final String createUser,
            @JsonProperty("updateUser") final String updateUser,
            @JsonProperty("description") final String description,
            @JsonProperty("scyllaDbRef") final DocRef scyllaDbRef) {
        super(type, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.scyllaDbRef = scyllaDbRef;
    }

    /**
     * @return A new {@link DocRef} for this document's type with the supplied uuid.
     */
    public static DocRef getDocRef(final String uuid) {
        return DocRef.builder(DOCUMENT_TYPE)
                .uuid(uuid)
                .build();
    }

    /**
     * @return A new builder for creating a {@link DocRef} for this document's type.
     */
    public static DocRef.TypedBuilder buildDocRef() {
        return DocRef.builder(DOCUMENT_TYPE);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public DocRef getScyllaDbRef() {
        return scyllaDbRef;
    }

    public void setScyllaDbRef(final DocRef scyllaDbRef) {
        this.scyllaDbRef = scyllaDbRef;
    }

    @JsonIgnore
    @Override
    public final String getType() {
        return DOCUMENT_TYPE;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StateDoc)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final StateDoc elasticIndex = (StateDoc) o;
        return Objects.equals(description, elasticIndex.description) &&
                Objects.equals(scyllaDbRef, elasticIndex.scyllaDbRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                description,
                scyllaDbRef);
    }

    @Override
    public String toString() {
        return "StateStoreDoc{" +
                "description='" + description + '\'' +
                ", scyllaDbRef='" + scyllaDbRef + '\'' +
                '}';
    }
}
