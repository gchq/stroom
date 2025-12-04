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

package stroom.explorer.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"docRef", "extension", "filter"})
@JsonInclude(Include.NON_NULL)
public class FetchHighlightsRequest {

    @JsonProperty
    private final DocRef docRef;
    @JsonProperty
    private final String extension;
    @JsonProperty
    private final StringMatch filter;

    @JsonCreator
    public FetchHighlightsRequest(@JsonProperty("docRef") final DocRef docRef,
                                  @JsonProperty("extension") final String extension,
                                  @JsonProperty("filter") final StringMatch filter) {
        this.docRef = docRef;
        this.extension = extension;
        this.filter = filter;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public String getExtension() {
        return extension;
    }

    public StringMatch getFilter() {
        return filter;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FetchHighlightsRequest that = (FetchHighlightsRequest) o;
        return Objects.equals(docRef, that.docRef) &&
                Objects.equals(extension, that.extension) &&
                Objects.equals(filter, that.filter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(docRef, extension, filter);
    }

    @Override
    public String toString() {
        return "FetchHighlightsRequest{" +
                "docRef=" + docRef +
                ", extension='" + extension + '\'' +
                ", filter=" + filter +
                '}';
    }
}
