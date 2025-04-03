package stroom.receive.common;

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the hashed form of a Data Feed Key, i.e. where we only have the
 * hash of the key and not the key itself.
 */
@JsonPropertyOrder(alphabetic = true)
public class HashedDataFeedKey {

    @JsonProperty
    @JsonPropertyDescription("The hash of the datafeed key. Hashed using hashAlgorithm.")
    private final String hash;

    @JsonProperty
    @JsonPropertyDescription("The hash algorithm ID used to hash the datafeed key. A zero padded 3 digit number.")
    private final String hashAlgorithmId;

//    @JsonProperty
//    @JsonPropertyDescription("The unique ID for the account sending the data to stroom. " +
//                             "An account may comprise multiple systems and components of systems. " +
//                             "This will be used to name auto-created folders and documents in Stroom")
//    private final String accountId;

    @JsonProperty
    @JsonPropertyDescription("A map of stream attribute key/value pairs. These will trump any entries " +
                             "in the stream headers.")
    private final Map<String, String> streamMetaData;

    @JsonProperty
    @JsonPropertyDescription("The date/time the key expires, expressed as milliseconds since the unix epoch.")
    private final long expiryDateEpochMs;

    @JsonCreator
    public HashedDataFeedKey(@JsonProperty("hash") final String hash,
                             @JsonProperty("hashAlgorithmId") final String hashAlgorithmId,
//                             @JsonProperty("accountId") final String accountId,
                             @JsonProperty("streamMetaData") final Map<String, String> streamMetaData,
                             @JsonProperty("expiryDateEpochMs") final long expiryDateEpochMs) {
        this.hash = hash;
        this.hashAlgorithmId = hashAlgorithmId;
//        this.accountId = accountId;
        this.streamMetaData = NullSafe.map(streamMetaData);
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

//    @NotBlank
//    public String getAccountId() {
//        return accountId;
//    }

    public Map<String, String> getStreamMetaData() {
        return streamMetaData;
    }

    @JsonIgnore
    public String getStreamMetaValue(final String metaKey) {
        return NullSafe.isNonBlankString(metaKey)
                ? streamMetaData.get(metaKey)
                : null;
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
        final HashedDataFeedKey that = (HashedDataFeedKey) object;
        return expiryDateEpochMs == that.expiryDateEpochMs
               && Objects.equals(hash, that.hash)
               && Objects.equals(hashAlgorithmId, that.hashAlgorithmId)
//               && Objects.equals(subjectId, that.subjectId)
//               && Objects.equals(displayName, that.displayName)
//               && Objects.equals(accountId, that.accountId)
               && Objects.equals(streamMetaData, that.streamMetaData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hash,
                hashAlgorithmId,
//                subjectId,
//                displayName,
//                accountId,
                streamMetaData,
                expiryDateEpochMs);
    }

    @Override
    public String toString() {
        return "DataFeedKey{" +
               "hash='" + hash + '\'' +
               ", hashAlgorithmId='" + hashAlgorithmId + '\'' +
//               ", subjectId='" + subjectId + '\'' +
//               ", displayName='" + displayName + '\'' +
//               ", accountId='" + accountId + '\'' +
               ", streamMetaData=" + streamMetaData +
               ", expiryDateEpochMs=" + expiryDateEpochMs +
               '}';
    }
}
