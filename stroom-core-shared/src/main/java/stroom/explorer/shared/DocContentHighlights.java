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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"docRef", "text", "highlights"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocContentHighlights {

    @JsonProperty
    private final DocRef docRef;
    @JsonProperty
    private final String text;
    @JsonProperty
    private final List<StringMatchLocation> highlights;

    @JsonCreator
    public DocContentHighlights(@JsonProperty("docRef") final DocRef docRef,
                                @JsonProperty("text") final String text,
                                @JsonProperty("highlights") final List<StringMatchLocation> highlights) {
        this.docRef = docRef;
        this.text = text;
        this.highlights = highlights;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public String getText() {
        return text;
    }

    public List<StringMatchLocation> getHighlights() {
        return highlights;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DocContentHighlights that = (DocContentHighlights) o;
        return Objects.equals(docRef, that.docRef) &&
                Objects.equals(text, that.text) &&
                Objects.equals(highlights, that.highlights);
    }

    @Override
    public int hashCode() {
        return Objects.hash(docRef, text, highlights);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private DocRef docRef;
        private String text;
        private List<StringMatchLocation> highlights;

        private Builder() {
        }

        private Builder(final DocContentHighlights docContentHighlights) {
            this.docRef = docContentHighlights.docRef;
            this.text = docContentHighlights.text;
            this.highlights = docContentHighlights.highlights;
        }

        public Builder docRef(final DocRef docRef) {
            this.docRef = docRef;
            return this;
        }

        public Builder text(final String text) {
            this.text = text;
            return this;
        }

        public Builder highlights(final List<StringMatchLocation> highlights) {
            this.highlights = highlights;
            return this;
        }

        public DocContentHighlights build() {
            return new DocContentHighlights(docRef, text, highlights);
        }
    }
}
