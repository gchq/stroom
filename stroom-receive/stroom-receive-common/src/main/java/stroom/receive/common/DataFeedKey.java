package stroom.receive.common;

import stroom.util.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

@JsonPropertyOrder(alphabetic = true)
public class DataFeedKey {

    @JsonProperty
    @JsonPropertyDescription("The hash of the datafeed key. Hashed using hashAlgorithm.")
    private final String hash;

    @JsonProperty
    @JsonPropertyDescription("The hash algorithm ID used to hash the datafeed key. A zero padded 3 digit number.")
    private final String hashAlgorithmId;

    @JsonProperty
    @JsonPropertyDescription("The unique subject ID of the user associated with the datafeed key.")
    private final String subjectId;

    @JsonProperty
    @JsonPropertyDescription("A more human friendly display form of the user identity. May be null.")
    private final String displayName;

    @JsonProperty
    @JsonPropertyDescription("The unique name for the system sending the data. This will be used name " +
            "auth-created folders and documents in Stroom")
    private final String systemName;

    @JsonProperty
    @JsonPropertyDescription(
            "A list of case sensitive regular expression patterns that will be used to verify the " +
                    "'Feed' header on data receipt. Only feeds matching one of these patterns will be accepted.")
    private final List<String> feedRegexPatterns;

    @JsonProperty
    @JsonPropertyDescription("A map of stream attribute key/value pairs. These will trump any entries " +
            "in the stream headers.")
    private final Map<String, String> streamMetaData;

    @JsonProperty
    @JsonPropertyDescription("The date the key expires, expressed as milliseconds since the unix epoch.")
    private final long expiryDateEpochMs;

    @JsonCreator
    public DataFeedKey(@JsonProperty("hash") final String hash,
                       @JsonProperty("hashAlgorithmId") final String hashAlgorithmId,
                       @JsonProperty("subjectId") final String subjectId,
                       @JsonProperty("displayName") final String displayName,
                       @JsonProperty("systemName") final String systemName,
                       @JsonProperty("feedRegexPatterns") final List<String> feedRegexPatterns,
                       @JsonProperty("streamMetaData") final Map<String, String> streamMetaData,
                       @JsonProperty("expiryDateEpochMs") final long expiryDateEpochMs) {
        this.hash = hash;
        this.hashAlgorithmId = hashAlgorithmId;
        this.subjectId = subjectId;
        this.displayName = displayName;
        this.systemName = systemName;
        this.feedRegexPatterns = feedRegexPatterns;
        this.streamMetaData = streamMetaData;
        this.expiryDateEpochMs = expiryDateEpochMs;
    }

    @NotBlank
    public String getHash() {
        return hash;
    }

    @NotBlank
    public String getHashAlgorithmId() {
        return hashAlgorithmId;
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

    @NotBlank
    public String getSystemName() {
        return systemName;
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
        return expiryDateEpochMs == that.expiryDateEpochMs
                && Objects.equals(hash, that.hash)
                && Objects.equals(hashAlgorithmId, that.hashAlgorithmId)
                && Objects.equals(subjectId, that.subjectId)
                && Objects.equals(displayName, that.displayName)
                && Objects.equals(systemName, that.systemName)
                && Objects.equals(feedRegexPatterns, that.feedRegexPatterns)
                && Objects.equals(streamMetaData, that.streamMetaData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hash,
                hashAlgorithmId,
                subjectId,
                displayName,
                systemName,
                feedRegexPatterns,
                streamMetaData,
                expiryDateEpochMs);
    }

    @Override
    public String toString() {
        return "DataFeedKey{" +
                "hash='" + hash + '\'' +
                ", hashAlgorithmId='" + hashAlgorithmId + '\'' +
                ", subjectId='" + subjectId + '\'' +
                ", displayName='" + displayName + '\'' +
                ", systemName='" + systemName + '\'' +
                ", feedRegexPatterns=" + feedRegexPatterns +
                ", streamMetaData=" + streamMetaData +
                ", expiryDateEpochMs=" + expiryDateEpochMs +
                '}';
    }
}
