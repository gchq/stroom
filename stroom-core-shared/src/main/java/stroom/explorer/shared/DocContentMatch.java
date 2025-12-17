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

@JsonPropertyOrder({"docRef", "extension", "location", "sample"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocContentMatch {

    private static final int SAMPLE_LENGTH_BEFORE = 40;
    private static final int SAMPLE_LENGTH_AFTER = 200;

    @JsonProperty
    private final DocRef docRef;
    @JsonProperty
    private final String extension;
    @JsonProperty
    private final StringMatchLocation location;
    @JsonProperty
    private final String sample;
    @JsonProperty
    private final boolean sampleAtStartOfLine;
    @JsonProperty
    private final List<String> tags;

    @JsonCreator
    public DocContentMatch(@JsonProperty("docRef") final DocRef docRef,
                           @JsonProperty("extension") final String extension,
                           @JsonProperty("location") final StringMatchLocation location,
                           @JsonProperty("sample") final String sample,
                           @JsonProperty("sampleAtStartOfLine") final boolean sampleAtStartOfLine,
                           @JsonProperty("tags") final List<String> tags) {
        this.docRef = docRef;
        this.extension = extension;
        this.location = location;
        this.sample = sample;
        this.sampleAtStartOfLine = sampleAtStartOfLine;
        this.tags = tags;
    }

    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    public static DocContentMatch create(final DocRef docRef,
                                         final String extension,
                                         final String text,
                                         final StringMatchLocation location,
                                         final List<String> tags) {
        int offset = location.getOffset();
        int length = location.getLength();
        final char[] chars = text.toCharArray();
        final int min = Math.max(0, offset - SAMPLE_LENGTH_BEFORE);
        int sampleStart = offset;
        // Go back to get a sample from the same line.
        boolean sampleAtStartOfLine = sampleStart == 0;
        for (; sampleStart >= min; sampleStart--) {
            final char c = chars[sampleStart];
            if (c == '\n') {
                sampleAtStartOfLine = true;
                break;
            }
        }
        sampleStart = Math.max(0, sampleStart);

        // Trim leading whitespace.
        for (; sampleStart < offset; sampleStart++) {
            final char c = chars[sampleStart];
            if (!Character.isWhitespace(c)) {
                break;
            }
        }

        // Adjust offset for start of sample.
        offset -= sampleStart;

        // Now remove newlines and adjust offset and length to accordingly.
        final StringBuilder sample = new StringBuilder();
        for (int i = sampleStart; i < chars.length; i++) {
            final char c = chars[i];
            if (c == '\r' || c == '\n') {
                break;

//            if (c == '\r') {
//                // Omit carriage returns.
//                if (i < location.getOffset()) {
//                    offset--;
//                } else if (i >= location.getOffset() && i <= location.getOffset() + location.getLength()) {
//                    length--;
//                }
//            } else if (c == '\n') {
//                sample.append(' ');
            } else {
                sample.append(c);
                if (sample.length() >= SAMPLE_LENGTH_BEFORE + SAMPLE_LENGTH_AFTER) {
                    break;
                }
            }
        }

        // Ensure offset and length are positive.
        offset = Math.max(offset, 0);
        length = Math.max(length, 0);

        final StringMatchLocation match = new StringMatchLocation(offset, length);
        return DocContentMatch
                .builder()
                .docRef(docRef)
                .extension(extension)
                .location(match)
                .sample(sample.toString())
                .sampleAtStartOfLine(sampleAtStartOfLine)
                .tags(tags)
                .build();
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public String getExtension() {
        return extension;
    }

    public StringMatchLocation getLocation() {
        return location;
    }

    public String getSample() {
        return sample;
    }

    public boolean isSampleAtStartOfLine() {
        return sampleAtStartOfLine;
    }

    public List<String> getTags() {
        return tags;
    }

    @Override
    public String toString() {
        return sample;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DocContentMatch that = (DocContentMatch) o;
        return Objects.equals(docRef, that.docRef)
                && Objects.equals(extension, that.extension) &&
                Objects.equals(location, that.location) &&
                Objects.equals(sample, that.sample);
    }

    @Override
    public int hashCode() {
        return Objects.hash(docRef, extension, location, sample);
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
        private String extension;
        private StringMatchLocation location;
        private String sample;
        private boolean sampleAtStartOfLine = true;
        private List<String> tags;

        private Builder() {
        }

        private Builder(final DocContentMatch docContentMatch) {
            this.docRef = docContentMatch.docRef;
            this.extension = docContentMatch.extension;
            this.location = docContentMatch.location;
            this.sample = docContentMatch.sample;
            this.sampleAtStartOfLine = docContentMatch.sampleAtStartOfLine;
        }

        public Builder docRef(final DocRef docRef) {
            this.docRef = docRef;
            return this;
        }

        public Builder extension(final String extension) {
            this.extension = extension;
            return this;
        }

        public Builder location(final StringMatchLocation location) {
            this.location = location;
            return this;
        }

        public Builder sample(final String sample) {
            this.sample = sample;
            return this;
        }

        public Builder sampleAtStartOfLine(final boolean sampleAtStartOfLine) {
            this.sampleAtStartOfLine = sampleAtStartOfLine;
            return this;
        }

        public Builder tags(final List<String> tags) {
            this.tags = tags;
            return this;
        }

        public DocContentMatch build() {
            return new DocContentMatch(docRef, extension, location, sample, sampleAtStartOfLine, tags);
        }
    }
}
