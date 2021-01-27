package stroom.pipeline.refdata.util;

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;
import java.util.Map;
import java.util.TreeMap;

@Singleton
public class ByteBufferPoolConfig extends AbstractConfig {
    // Use a treemap so we get a consistent order in the yaml so TestYamlUtil doesn't fail
    private Map<Integer, Integer> pooledByteBufferCounts = new TreeMap<>(Map.of(
            1, 50,
            10, 50,
            100, 50,
            1_000, 50,
            10_000, 50,
            100_000, 10,
            1_000_000, 3));

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("Defines the maximum number of byte buffers that will be held in the pool, keyed by the " +
            "size of the buffer. Configured buffer sizes must be a power of ten (i.e. 1, 10, 100, etc.) or they will be ignored. " +
            "Values should be greater than or equal to zero. Set the count to zero to indicate that a buffer size " +
            "should not be pooled. An empty or null map means no buffers will be pooled. " +
            "Keys should be contiguous powers of ten from one upwards, else any gaps will be assigned a default value of 50.")
    public Map<Integer, Integer> getPooledByteBufferCounts() {
        return pooledByteBufferCounts;
    }

    public void setPooledByteBufferCounts(final Map<Integer, Integer> pooledByteBufferCounts) {
        this.pooledByteBufferCounts = pooledByteBufferCounts;
    }

    @Override
    public String toString() {
        return "ByteBufferPoolConfig{" +
                "pooledByteBufferCounts=" + pooledByteBufferCounts +
                '}';
    }
}