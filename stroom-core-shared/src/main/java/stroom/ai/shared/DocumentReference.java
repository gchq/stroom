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

package stroom.ai.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/// A Stroom document attached to a chat as a reference: what it is and where it is in the Explorer tree. The
/// chat is given a short note about it rather than its content, which the agent reads with its document tools.
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class DocumentReference {

    @JsonProperty
    private final DocRef docRef;
    @JsonProperty
    private final String path;

    /// @param docRef The document.
    /// @param path   Its folder's path in the Explorer tree, e.g. `/Security/Lookups`, or null if not known.
    @JsonCreator
    public DocumentReference(@JsonProperty("docRef") final DocRef docRef,
                             @JsonProperty("path") final String path) {
        this.docRef = docRef;
        this.path = path;
    }

    /// @return The document.
    public DocRef getDocRef() {
        return docRef;
    }

    /// @return Its folder's path in the Explorer tree, or null if not known.
    public String getPath() {
        return path;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DocumentReference that = (DocumentReference) o;
        return Objects.equals(docRef, that.docRef)
               && Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(docRef, path);
    }

    @Override
    public String toString() {
        return "DocumentReference{docRef=" + docRef + ", path='" + path + "'}";
    }
}
