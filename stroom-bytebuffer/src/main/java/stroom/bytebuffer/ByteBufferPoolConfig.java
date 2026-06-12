/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

    private final int warningThresholdPercentage;
    private final Map<Integer, Integer> pooledByteBufferCounts;
    private final boolean blockOnExhaustedPool;

    public ByteBufferPoolConfig() {
        warningThresholdPercentage = 90;
        // Use a treemap so we get a consistent order in the yaml so TestYamlUtil doesn't fail
        pooledByteBufferCounts = new TreeMap<>(Map.of(
                1, 50,
                10, 50,
                100, 50,
                1_000, 50,
                10_000, 50,
                100_000, 10,
                1_000_000, 3));
        blockOnExhaustedPool = false;
    }

    @JsonCreator
    public ByteBufferPoolConfig(
            @JsonProperty("warningThresholdPercentage") final int warningThresholdPercentage,
            @JsonProperty("pooledByteBufferCounts") final Map<Integer, Integer> pooledByteBufferCounts,
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
            "keyed by the size of the buffer. Configured buffer sizes must be a power of ten " +
            "(i.e. 1, 10, 100, etc.) or they will be ignored. Values should be greater than or equal to zero. " +
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
