package stroom.docref;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"docRef", "matchOffset", "matchLength", "sample"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocContentMatch {

    @JsonProperty
    private final DocRef docRef;
    @JsonProperty
    private final long matchOffset;
    @JsonProperty
    private final long matchLength;
    @JsonProperty
    private final String sample;

    @JsonCreator
    public DocContentMatch(@JsonProperty("docRef") final DocRef docRef,
                           @JsonProperty("matchOffset") final long matchOffset,
                           @JsonProperty("matchLength") final long matchLength,
                           @JsonProperty("sample") final String sample) {
        this.docRef = docRef;
        this.matchOffset = matchOffset;
        this.matchLength = matchLength;
        this.sample = sample;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public long getMatchOffset() {
        return matchOffset;
    }

    public long getMatchLength() {
        return matchLength;
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
        return matchOffset == that.matchOffset && matchLength == that.matchLength && Objects.equals(docRef,
                that.docRef) && Objects.equals(sample, that.sample);
    }

    @Override
    public int hashCode() {
        return Objects.hash(docRef, matchOffset, matchLength, sample);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private DocRef docRef;
        private long matchOffset;
        private long matchLength;
        private String sample;

        private Builder() {
        }

        private Builder(final DocContentMatch docContentMatch) {
            this.docRef = docContentMatch.docRef;
            this.matchOffset = docContentMatch.matchOffset;
            this.matchLength = docContentMatch.matchLength;
            this.sample = docContentMatch.sample;
        }

        public Builder docRef(final DocRef docRef) {
            this.docRef = docRef;
            return this;
        }

        public Builder matchOffset(final long matchOffset) {
            this.matchOffset = matchOffset;
            return this;
        }

        public Builder matchLength(final long matchLength) {
            this.matchLength = matchLength;
            return this;
        }

        public Builder sample(final String sample) {
            this.sample = sample;
            return this;
        }

        public DocContentMatch build() {
            return new DocContentMatch(docRef, matchOffset, matchLength, sample);
        }
    }
}
