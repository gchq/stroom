package stroom.docref;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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

    @JsonCreator
    public DocContentMatch(@JsonProperty("docRef") final DocRef docRef,
                           @JsonProperty("extension") final String extension,
                           @JsonProperty("location") final StringMatchLocation location,
                           @JsonProperty("sample") final String sample) {
        this.docRef = docRef;
        this.extension = extension;
        this.location = location;
        this.sample = sample;
    }

    public static DocContentMatch create(final DocRef docRef,
                                         final String extension,
                                         final String text,
                                         final StringMatchLocation location) {
        final char[] chars = text.toCharArray();
        final int min = Math.max(0, location.getOffset() - SAMPLE_LENGTH_BEFORE);
        int sampleStart = location.getOffset();
        // Go back to get a sample from the same line.
        for (; sampleStart >= min; sampleStart--) {
            char c = chars[sampleStart];
            if (c == '\n') {
                break;
            }
        }
        sampleStart = Math.max(0, sampleStart);

        // Trim leading whitespace.
        for (; sampleStart < location.getOffset(); sampleStart++) {
            char c = chars[sampleStart];
            if (!Character.isWhitespace(c)) {
                break;
            }
        }
        int sampleEnd = location.getOffset();
        int max = Math.min(text.length(), location.getOffset() + SAMPLE_LENGTH_AFTER);
        for (; sampleEnd < max; sampleEnd++) {
            char c = chars[sampleEnd];
            if (c == '\n') {
                break;
            }
        }
        sampleEnd = Math.min(text.length(), sampleEnd);

        final String sample = text.substring(sampleStart, sampleEnd);
        final StringMatchLocation match = new StringMatchLocation(
                location.getOffset() - sampleStart,
                location.getLength());
        return DocContentMatch
                .builder()
                .docRef(docRef)
                .extension(extension)
                .location(match)
                .sample(sample)
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

    public static class Builder {

        private DocRef docRef;
        private String extension;
        private StringMatchLocation location;
        private String sample;

        private Builder() {
        }

        private Builder(final DocContentMatch docContentMatch) {
            this.docRef = docContentMatch.docRef;
            this.extension = docContentMatch.extension;
            this.location = docContentMatch.location;
            this.sample = docContentMatch.sample;
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

        public DocContentMatch build() {
            return new DocContentMatch(docRef, extension, location, sample);
        }
    }
}
