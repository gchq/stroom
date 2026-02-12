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

package stroom.docstore.shared;

import stroom.docref.DocRef;
import stroom.util.shared.Embeddable;

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
        "embeddedIn"})
@JsonInclude(Include.NON_NULL)
public abstract class AbstractEmbeddableDoc extends AbstractDoc implements Embeddable {

    @JsonProperty
    private DocRef embeddedIn;

    @JsonCreator
    public AbstractEmbeddableDoc(@JsonProperty("type") final String type,
                                 @JsonProperty("uuid") final String uuid,
                                 @JsonProperty("name") final String name,
                                 @JsonProperty("version") final String version,
                                 @JsonProperty("createTimeMs") final Long createTimeMs,
                                 @JsonProperty("updateTimeMs") final Long updateTimeMs,
                                 @JsonProperty("createUser") final String createUser,
                                 @JsonProperty("updateUser") final String updateUser,
                                 @JsonProperty("embeddedIn") final DocRef embeddedIn) {
        super(type, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.embeddedIn = embeddedIn;
    }

    @Override
    public void setEmbeddedIn(final DocRef embeddedIn) {
        this.embeddedIn = embeddedIn;
    }

    @Override
    public DocRef getEmbeddedIn() {
        return embeddedIn;
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
        final AbstractEmbeddableDoc doc = (AbstractEmbeddableDoc) o;
        return Objects.equals(embeddedIn, doc.embeddedIn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), embeddedIn);
    }
}
