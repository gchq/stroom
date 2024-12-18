package stroom.receive.common;

import stroom.util.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class DataFeedKey {

    @JsonProperty
    @JsonPropertyDescription("The hash of the datafeed key. Hashed using hashAlgorithm.")
    private final String hash;

    @JsonProperty
    @JsonPropertyDescription("The hash algorithm used to hash the datafeed key.")
    private final String hashAlgorithm;

    @JsonProperty
    @JsonPropertyDescription("The unique subject ID of the user associated with the datafeed key.")
    private final String subjectId;

    @JsonProperty
    @JsonPropertyDescription("A more human friendly display form of the user identity. May be null.")
    private final String displayName;

    @JsonProperty
    @JsonPropertyDescription(
            "A list of case sensitive regular expression patterns that will be used to verify the " +
            "'Feed' header on data receipt. Only feeds matching one of these patterns will be accepted.")
    private final List<String> feedRegexPatterns;

    @JsonProperty
    @JsonPropertyDescription("A map of stream attribute key/value pairs.")
    private final Map<String, String> streamMetaData;

    @JsonProperty
    @JsonPropertyDescription("The date the key expires, expressed as milliseconds since the unix epoch.")
    private final long expiryDateEpochMs;

    @JsonCreator
    public DataFeedKey(@JsonProperty("hash") final String hash,
                       @JsonProperty("hashAlgorithm") final String hashAlgorithm,
                       @JsonProperty("subjectId") final String subjectId,
                       @JsonProperty("displayName") final String displayName,
                       @JsonProperty("feedRegexPatterns") final List<String> feedRegexPatterns,
                       @JsonProperty("streamMetaData") final Map<String, String> streamMetaData,
                       @JsonProperty("expiryDateEpochMs") final long expiryDateEpochMs) {
        this.hash = hash;
        this.hashAlgorithm = hashAlgorithm;
        this.subjectId = subjectId;
        this.displayName = displayName;
        this.feedRegexPatterns = feedRegexPatterns;
        this.streamMetaData = streamMetaData;
        this.expiryDateEpochMs = expiryDateEpochMs;
    }

    @NotBlank
    public String getHash() {
        return hash;
    }

    @NotBlank
    public String getHashAlgorithm() {
        return hashAlgorithm;
    }

    @NotBlank
    public String getSubjectId() {
        return subjectId;
    }

    /**
     * May be null.
     */
    public String getDisplayName() {
        return displayName;
    }

    public List<String> getFeedRegexPatterns() {
        return NullSafe.list(feedRegexPatterns);
    }

    public Map<String, String> getStreamMetaData() {
        return NullSafe.map(streamMetaData);
    }

    @Min(0)
    public long getExpiryDateEpochMs() {
        return expiryDateEpochMs;
    }

    @JsonIgnore
    public Instant getExpiryDate() {
        return Instant.ofEpochMilli(expiryDateEpochMs);
    }

    @JsonIgnore
    public boolean isExpired() {
        return Instant.now().isAfter(getExpiryDate());
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final DataFeedKey that = (DataFeedKey) object;
        return Objects.equals(hash, that.hash)
               && Objects.equals(hashAlgorithm, that.hashAlgorithm)
               && Objects.equals(subjectId, that.subjectId)
               && Objects.equals(displayName, that.displayName)
               && Objects.equals(feedRegexPatterns, that.feedRegexPatterns)
               && Objects.equals(streamMetaData, that.streamMetaData)
               && expiryDateEpochMs == that.expiryDateEpochMs;
    }

    @Override
    public int hashCode() {
        return Objects.hash(hash, hashAlgorithm, subjectId, feedRegexPatterns, streamMetaData, expiryDateEpochMs);
    }

    @Override
    public String toString() {
        return "DatafeedKey{" +
               "hash='" + hash + '\'' +
               ", hashAlgorithm='" + hashAlgorithm + '\'' +
               ", subjectId='" + subjectId + '\'' +
               ", displayName='" + displayName + '\'' +
               ", feedRegexPatterns=" + feedRegexPatterns +
               ", streamMetaData=" + streamMetaData +
               ", expiryDateEpochMs=" + expiryDateEpochMs +
               '}';
    }
}
