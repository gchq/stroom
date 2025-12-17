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

package stroom.pathways.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class DeletePathway {

    @JsonProperty
    private final DocRef docRef;
    @JsonProperty
    private final String name;

    @JsonCreator
    public DeletePathway(@JsonProperty("docRef") final DocRef docRef,
                         @JsonProperty("name") final String name) {
        this.docRef = docRef;
        this.name = name;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DeletePathway that = (DeletePathway) o;
        return Objects.equals(docRef, that.docRef) &&
               Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(docRef, name);
    }

    @Override
    public String toString() {
        return "DeletePathway{" +
               "docRef=" + docRef +
               ", name=" + name +
               '}';
    }
}
