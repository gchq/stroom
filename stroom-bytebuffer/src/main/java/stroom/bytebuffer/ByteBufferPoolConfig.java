package stroom.bytebuffer;

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.Map;
import java.util.TreeMap;

@JsonPropertyOrder(alphabetic = true)
public class ByteBufferPoolConfig extends AbstractConfig implements IsStroomConfig {

    public static final String PROP_NAME_BUFFER_COUNTS = "pooledByteBufferCounts";

    private final int warningThresholdPercentage;
    private final Map<Integer, Integer> pooledByteBufferCounts;
    private final boolean blockOnExhaustedPool;

    public ByteBufferPoolConfig() {
        warningThresholdPercentage = 90;
        // Use a treemap, so we get a consistent order in the yaml so TestYamlUtil doesn't fail
        pooledByteBufferCounts = new TreeMap<>(Map.ofEntries(
                Map.entry(4, 1_000),
                Map.entry(8, 1_000),
                Map.entry(16, 1_000),
                Map.entry(32, 1_000),
                Map.entry(64, 1_000),
                Map.entry(128, 1_000),
                Map.entry(256, 1_000),
                Map.entry(512, 1_000),
                Map.entry(1_024, 1_000),
                Map.entry(2_048, 500),
                Map.entry(4_096, 500),
                Map.entry(8_192, 100),
                Map.entry(16_384, 100),
                Map.entry(32_768, 10),
                Map.entry(65_536, 10)));
        blockOnExhaustedPool = false;
    }

    @JsonCreator
    public ByteBufferPoolConfig(
            @JsonProperty("warningThresholdPercentage") final int warningThresholdPercentage,
            @JsonProperty(PROP_NAME_BUFFER_COUNTS) final Map<Integer, Integer> pooledByteBufferCounts,
            @JsonProperty("blockOnExhaustedPool") final boolean blockOnExhaustedPool) {
        this.warningThresholdPercentage = warningThresholdPercentage;
        this.pooledByteBufferCounts = pooledByteBufferCounts;
        this.blockOnExhaustedPool = blockOnExhaustedPool;
    }

    @Max(100)
    @Min(1)
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("When the number of created buffers for any size reaches this threshold a warning will " +
            "be logged.")
    public int getWarningThresholdPercentage() {
        return warningThresholdPercentage;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("Defines the maximum number of byte buffers that will be held in the pool, " +
            "keyed by the size of the buffer. Configured buffer sizes must be a power of ten and >= 10, " +
            "(i.e. 10, 100, etc.) or they will be ignored. Values should be greater than or equal to zero. " +
            "Set the count to zero to indicate that a buffer size should not be pooled. An empty or null map " +
            "means no buffers will be pooled. Keys should be contiguous powers of ten from one upwards, else " +
            "any gaps will be assigned a default value of 50.")
    public Map<Integer, Integer> getPooledByteBufferCounts() {
        return pooledByteBufferCounts;
    }

    @JsonPropertyDescription("Whether the thread should be blocked when requesting a buffer from the pool and the " +
            "limit for that buffer size has been reached. If false new buffers will be created and excess buffers " +
            "will have to be destroyed when no longer needed which may have a performance/memory penalty.")
    public boolean isBlockOnExhaustedPool() {
        return blockOnExhaustedPool;
    }

    public ByteBufferPoolConfig withPooledByteBufferCounts(final Map<Integer, Integer> pooledByteBufferCounts) {
        return new ByteBufferPoolConfig(warningThresholdPercentage, pooledByteBufferCounts, blockOnExhaustedPool);
    }

    public ByteBufferPoolConfig withBlockOnExhaustedPool(final boolean blockOnExhaustedPool) {
        return new ByteBufferPoolConfig(warningThresholdPercentage, pooledByteBufferCounts, blockOnExhaustedPool);
    }

    @Override
    public String toString() {
        return "ByteBufferPoolConfig{" +
                "warningThresholdPercentage=" + warningThresholdPercentage +
                ", pooledByteBufferCounts=" + pooledByteBufferCounts +
                ", blockOnExhaustedPool=" + blockOnExhaustedPool +
                '}';
    }
}
