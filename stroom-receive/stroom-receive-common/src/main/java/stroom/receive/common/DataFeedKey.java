package stroom.receive.common;

import stroom.util.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
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
    @JsonPropertyDescription("The unique name for the account sending the data to stroom. " +
            "An account may comprise multiple systems and components of systems. " +
            "This will be used to name auto-created folders and documents in Stroom")
    private final String accountName;

    @JsonProperty
    @JsonPropertyDescription("A map of stream attribute key/value pairs. These will trump any entries " +
            "in the stream headers.")
    private final Map<String, String> streamMetaData;

    @JsonProperty
    @JsonPropertyDescription("The date/time the key expires, expressed as milliseconds since the unix epoch.")
    private final long expiryDateEpochMs;

    @JsonCreator
    public DataFeedKey(@JsonProperty("hash") final String hash,
                       @JsonProperty("hashAlgorithmId") final String hashAlgorithmId,
                       @JsonProperty("subjectId") final String subjectId,
                       @JsonProperty("displayName") final String displayName,
                       @JsonProperty("accountName") final String accountName,
                       @JsonProperty("streamMetaData") final Map<String, String> streamMetaData,
                       @JsonProperty("expiryDateEpochMs") final long expiryDateEpochMs) {
        this.hash = hash;
        this.hashAlgorithmId = hashAlgorithmId;
        this.subjectId = subjectId;
        this.displayName = displayName;
        this.accountName = accountName;
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
    public String getAccountName() {
        return accountName;
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
                && Objects.equals(accountName, that.accountName)
                && Objects.equals(streamMetaData, that.streamMetaData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hash,
                hashAlgorithmId,
                subjectId,
                displayName,
                accountName,
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
                ", accountName='" + accountName + '\'' +
                ", streamMetaData=" + streamMetaData +
                ", expiryDateEpochMs=" + expiryDateEpochMs +
                '}';
    }
}
