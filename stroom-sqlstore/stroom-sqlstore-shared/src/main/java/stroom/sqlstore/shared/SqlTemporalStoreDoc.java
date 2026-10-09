/*
 * Copyright 2026 Crown Copyright
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

package stroom.sqlstore.shared;

import stroom.docref.DocRef;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;

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
        "description"
})
@JsonInclude(Include.NON_NULL)
public class SqlTemporalStoreDoc extends AbstractDoc {

    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.SQL_TEMPORAL_STORE_DOCUMENT_TYPE;
    public static final String TYPE = DOCUMENT_TYPE.getType();

    @JsonProperty
    private final String description;

    @JsonCreator
    public SqlTemporalStoreDoc(
            @JsonProperty("uuid") final String uuid,
            @JsonProperty("name") final String name,
            @JsonProperty("version") final String version,
            @JsonProperty("createTimeMs") final Long createTimeMs,
            @JsonProperty("updateTimeMs") final Long updateTimeMs,
            @JsonProperty("createUser") final String createUser,
            @JsonProperty("updateUser") final String updateUser,
            @JsonProperty("description") final String description) {
        super(TYPE, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
    }

    public String getDescription() {
        return description;
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
        final SqlTemporalStoreDoc that = (SqlTemporalStoreDoc) o;
        return Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description);
    }

    public DocRef asDocRef() {
        return DocRef.builder()
                .type(TYPE)
                .uuid(getUuid())
                .name(getName())
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder extends AbstractBuilder<SqlTemporalStoreDoc, Builder> {

        private String description;

        public Builder() {
        }

        public Builder(final SqlTemporalStoreDoc doc) {
            super(doc);
            this.description = doc.description;
        }

        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public SqlTemporalStoreDoc build() {
            return new SqlTemporalStoreDoc(
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    description);
        }
    }
}
