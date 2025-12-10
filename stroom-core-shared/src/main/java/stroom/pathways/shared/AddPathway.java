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
import stroom.pathways.shared.pathway.Pathway;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class AddPathway {

    @JsonProperty
    private final DocRef docRef;
    @JsonProperty
    private final Pathway pathway;

    @JsonCreator
    public AddPathway(@JsonProperty("docRef") final DocRef docRef,
                      @JsonProperty("pathway") final Pathway pathway) {
        this.docRef = docRef;
        this.pathway = pathway;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public Pathway getPathway() {
        return pathway;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AddPathway that = (AddPathway) o;
        return Objects.equals(docRef, that.docRef) &&
               Objects.equals(pathway, that.pathway);
    }

    @Override
    public int hashCode() {
        return Objects.hash(docRef, pathway);
    }

    @Override
    public String toString() {
        return "AddPathway{" +
               "docRef=" + docRef +
               ", pathway=" + pathway +
               '}';
    }
}
