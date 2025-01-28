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

    public CachedHashedDataFeedKey(final HashedDataFeedKey hashedDataFeedKey, final Path sourceFile) {
        this.hashedDataFeedKey = Objects.requireNonNull(hashedDataFeedKey);
        this.sourceFile = Objects.requireNonNull(sourceFile);
    }

    public HashedDataFeedKey getDataFeedKey() {
        return hashedDataFeedKey;
    }

    public Path getSourceFile() {
        return sourceFile;
    }

    @NotBlank
    public String getHash() {
        return hashedDataFeedKey.getHash();
    }

    @NotBlank
    public String getHashAlgorithmId() {
        return hashedDataFeedKey.getHashAlgorithmId();
    }

    @NotBlank
    public String getAccountId() {
        return hashedDataFeedKey.getAccountId();
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

    @JsonIgnore
    public Instant getExpiryDate() {
        return hashedDataFeedKey.getExpiryDate();
    }

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
        return Objects.equals(hashedDataFeedKey, that.hashedDataFeedKey) && Objects.equals(sourceFile,
                that.sourceFile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hashedDataFeedKey, sourceFile);
    }

    @Override
    public String toString() {
        return "CachedDataFeedKey{" +
               "dataFeedKey=" + hashedDataFeedKey +
               ", sourceFile=" + sourceFile +
               '}';
    }
}
