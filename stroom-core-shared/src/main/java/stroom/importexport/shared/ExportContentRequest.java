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

package stroom.importexport.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Set;

/// A request to export content.
///
/// The JSON form is a superset of `DocRefs`, so a body of `{"docRefs": [...]}`
/// is still accepted and exports everything below the supplied docRefs.
@JsonInclude(Include.NON_NULL)
public class ExportContentRequest {

    @JsonProperty
    private final Set<DocRef> docRefs;
    @JsonProperty
    private final Set<String> includedTypes;

    /// @param docRefs       The docRefs to export. Folders are exported along with
    ///                      everything below them.
    /// @param includedTypes The document types to export. If null, all types are
    ///                      exported. Folders are always recursed into, but are only
    ///                      exported in their own right if the folder type is included.
    @JsonCreator
    public ExportContentRequest(@JsonProperty("docRefs") final Set<DocRef> docRefs,
                                @JsonProperty("includedTypes") final Set<String> includedTypes) {
        this.docRefs = docRefs;
        this.includedTypes = includedTypes;
    }

    /// @return The docRefs to export.
    public Set<DocRef> getDocRefs() {
        return docRefs;
    }

    /// @return The document types to export, or null if all types are to be exported.
    public Set<String> getIncludedTypes() {
        return includedTypes;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ExportContentRequest that = (ExportContentRequest) o;
        return Objects.equals(docRefs, that.docRefs)
               && Objects.equals(includedTypes, that.includedTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(docRefs, includedTypes);
    }

    @Override
    public String toString() {
        return "ExportContentRequest{" +
               "docRefs=" + docRefs +
               ", includedTypes=" + includedTypes +
               '}';
    }
}
