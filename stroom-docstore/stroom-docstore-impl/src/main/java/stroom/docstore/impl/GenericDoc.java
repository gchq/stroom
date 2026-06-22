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

package stroom.docstore.impl;

import stroom.docstore.shared.AbstractDoc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Minimal doc class that deserialises only common {@link AbstractDoc} fields.
 * Used for info() lookups where type-specific fields are not needed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GenericDoc extends AbstractDoc {

    @JsonCreator
    public GenericDoc(@JsonProperty("type") final String type,
                      @JsonProperty("uuid") final String uuid,
                      @JsonProperty("name") final String name,
                      @JsonProperty("version") final String version,
                      @JsonProperty("createTimeMs") final Long createTimeMs,
                      @JsonProperty("updateTimeMs") final Long updateTimeMs,
                      @JsonProperty("createUser") final String createUser,
                      @JsonProperty("updateUser") final String updateUser) {
        super(type, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends AbstractBuilder<GenericDoc, Builder> {

        private String type;

        private Builder() {
        }

        private Builder(final GenericDoc genericDoc) {
            this.type = genericDoc.getType();
            super(genericDoc);
        }

        public Builder type(final String type) {
            this.type = type;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public GenericDoc build() {
            return new GenericDoc(
                    type,
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser);
        }
    }
}
