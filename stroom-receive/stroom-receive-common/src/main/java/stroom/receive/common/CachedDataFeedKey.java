package stroom.receive.common;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

public class CachedDataFeedKey {

    private final DataFeedKey dataFeedKey;
    private final Path sourceFile;

    public CachedDataFeedKey(final DataFeedKey dataFeedKey, final Path sourceFile) {
        this.dataFeedKey = Objects.requireNonNull(dataFeedKey);
        this.sourceFile = Objects.requireNonNull(sourceFile);
    }

    public DataFeedKey getDataFeedKey() {
        return dataFeedKey;
    }

    public Path getSourceFile() {
        return sourceFile;
    }

    @NotBlank
    public String getHash() {
        return dataFeedKey.getHash();
    }

    @NotBlank
    public String getHashAlgorithmId() {
        return dataFeedKey.getHashAlgorithmId();
    }

    @NotBlank
    public String getSubjectId() {
        return dataFeedKey.getSubjectId();
    }

    public String getDisplayName() {
        return dataFeedKey.getDisplayName();
    }

    public List<String> getFeedRegexPatterns() {
        return dataFeedKey.getFeedRegexPatterns();
    }

    public Map<String, String> getStreamMetaData() {
        return dataFeedKey.getStreamMetaData();
    }

    @Min(0)
    public long getExpiryDateEpochMs() {
        return dataFeedKey.getExpiryDateEpochMs();
    }

    @JsonIgnore
    public Instant getExpiryDate() {
        return dataFeedKey.getExpiryDate();
    }

    @JsonIgnore
    public boolean isExpired() {
        return dataFeedKey.isExpired();
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final CachedDataFeedKey that = (CachedDataFeedKey) object;
        return Objects.equals(dataFeedKey, that.dataFeedKey) && Objects.equals(sourceFile,
                that.sourceFile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataFeedKey, sourceFile);
    }

    @Override
    public String toString() {
        return "CachedDataFeedKey{" +
                "dataFeedKey=" + dataFeedKey +
                ", sourceFile=" + sourceFile +
                '}';
    }
}
