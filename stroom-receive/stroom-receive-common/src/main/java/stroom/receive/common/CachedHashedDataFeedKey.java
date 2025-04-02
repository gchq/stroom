package stroom.receive.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public class CachedHashedDataFeedKey {

    private final HashedDataFeedKey hashedDataFeedKey;
    private final Path sourceFile;

    @JsonIgnore
    private final int hashCode;

    public CachedHashedDataFeedKey(final HashedDataFeedKey hashedDataFeedKey,
                                   final Path sourceFile) {
        this.hashedDataFeedKey = Objects.requireNonNull(hashedDataFeedKey);
        this.sourceFile = Objects.requireNonNull(sourceFile);
        this.hashCode = Objects.hash(hashedDataFeedKey, sourceFile);
    }

    public HashedDataFeedKey getDataFeedKey() {
        return hashedDataFeedKey;
    }

    /**
     * @return The path to the file that this {@link HashedDataFeedKey} was loaded from.
     */
    public Path getSourceFile() {
        return sourceFile;
    }

    /**
     * @return The hash of the data feed key. The hash algorithm used is defined by
     * {@link CachedHashedDataFeedKey#getHashAlgorithmId()}
     */
    @NotBlank
    public String getHash() {
        return hashedDataFeedKey.getHash();
    }

    @NotBlank
    public String getHashAlgorithmId() {
        return hashedDataFeedKey.getHashAlgorithmId();
    }

    /**
     * @return The value of a specified meta key.
     */
    @NotBlank
    public String getStreamMetaValue(final String metaKey) {
        return hashedDataFeedKey.getStreamMetaValue(metaKey);
    }

    //    @NotBlank
//    public String getSubjectId() {
//        return hashedDataFeedKey.getSubjectId();
//    }
//
//    public String getDisplayName() {
//        return hashedDataFeedKey.getDisplayName();
//    }

    public Map<String, String> getStreamMetaData() {
        return hashedDataFeedKey.getStreamMetaData();
    }

    @Min(0)
    public long getExpiryDateEpochMs() {
        return hashedDataFeedKey.getExpiryDateEpochMs();
    }

    /**
     * @return The expiry date of this data feed key
     */
    @JsonIgnore
    public Instant getExpiryDate() {
        return hashedDataFeedKey.getExpiryDate();
    }

    /**
     * @return True if this data feed key has expired
     */
    @JsonIgnore
    public boolean isExpired() {
        return hashedDataFeedKey.isExpired();
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final CachedHashedDataFeedKey that = (CachedHashedDataFeedKey) object;
        return Objects.equals(hashedDataFeedKey, that.hashedDataFeedKey)
               && Objects.equals(sourceFile, that.sourceFile);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return "CachedDataFeedKey{" +
               "dataFeedKey=" + hashedDataFeedKey +
               ", sourceFile=" + sourceFile +
               '}';
    }
}
