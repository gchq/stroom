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

package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.OptionalDouble;
import java.util.OptionalLong;

/**
 * Information on the capacity of an object, including the total and amount used.
 * As a minimum implementors need to implement {@link HasCapacityInfo#getCapacityUsedBytes()}
 * and {@link HasCapacityInfo#getTotalCapacityBytes()}. They need to implement
 * {@link HasCapacityInfo#getCapacityLimitBytes()} if a concept of a limit exists for the
 * implementation.
 */
public interface HasCapacityInfo {

    long DEFAULT_HEADROOM_BYTES = 1024L * 1024L * 1024L * 10L; // 10Gib
    double DEFAULT_HEADROOM_FRACTION = 0.01D; // i.e allow up to 99% used.

    HasCapacityInfo UNKNOWN = new HasCapacityInfo() {
        @Override
        public OptionalLong getCapacityUsedBytes() {
            return OptionalLong.empty();
        }

        @Override
        public OptionalLong getCapacityLimitBytes() {
            return OptionalLong.empty();
        }

        @Override
        public OptionalLong getTotalCapacityBytes() {
            return OptionalLong.empty();
        }
    };

    /**
     * @return The number of bytes currently used.
     */
    OptionalLong getCapacityUsedBytes();

    /**
     * @return The configured limit of usable bytes. This should be less than totalBytes.
     * A limit may not be set.
     */
    OptionalLong getCapacityLimitBytes();

    /**
     * @return The total bytes that this storage medium can accommodate when full.
     */
    OptionalLong getTotalCapacityBytes();

    /**
     * @return The number of bytes available until the limit is reached. If there is no limit set
     * then it is the number of bytes available until the storage medium is full.
     */
    @JsonIgnore
    default OptionalLong getFreeCapacityBytes() {
        final OptionalLong optLimitBytes = getCapacityLimitBytes();
        if (optLimitBytes.isPresent()) {
            final OptionalLong optBytesUsed = getCapacityUsedBytes();
            if (optBytesUsed.isPresent()) {
                return OptionalLong.of(optLimitBytes.getAsLong() - optBytesUsed.getAsLong());
            } else {
                return OptionalLong.empty();
            }
        } else {
            final OptionalLong optTotalBytes = getTotalCapacityBytes();
            if (optTotalBytes.isPresent()) {
                final OptionalLong optBytesUsed = getCapacityUsedBytes();
                if (optBytesUsed.isPresent()) {
                    return OptionalLong.of(optTotalBytes.getAsLong() - optBytesUsed.getAsLong());
                } else {
                    return OptionalLong.empty();
                }
            } else {
                return OptionalLong.empty();
            }
        }
    }

    /**
     * @return Percentage of available capacity until the limit is reached. If there is no limit set
     * then it is the percentages available until the storage medium is full.
     */
    @JsonIgnore
    default OptionalDouble getFreeCapacityPercent() {
        final OptionalLong optLimitBytes = getCapacityLimitBytes();
        if (optLimitBytes.isPresent()) {
            final OptionalLong optBytesUsed = getCapacityUsedBytes();
            if (optBytesUsed.isPresent()) {
                final long limitBytes = optLimitBytes.getAsLong();
                final long bytesUsed = optBytesUsed.getAsLong();
                return OptionalDouble.of((limitBytes - bytesUsed) / (double) limitBytes * 100);
            } else {
                return OptionalDouble.empty();
            }
        } else {
            final OptionalLong optTotalBytes = getTotalCapacityBytes();
            if (optTotalBytes.isPresent()) {
                final OptionalLong optBytesUsed = getCapacityUsedBytes();
                if (optBytesUsed.isPresent()) {
                    final long totalBytes = optTotalBytes.getAsLong();
                    final long bytesUsed = optBytesUsed.getAsLong();
                    return OptionalDouble.of((totalBytes - bytesUsed) / (double) totalBytes * 100);
                } else {
                    return OptionalDouble.empty();
                }
            } else {
                return OptionalDouble.empty();
            }
        }
    }

    /**
     * @return The use as a percentage of the limit or if there is no limit of the total.
     */
    @JsonIgnore
    default OptionalDouble getUsedCapacityPercent() {
        final OptionalLong optLimitBytes = getCapacityLimitBytes();
        final OptionalLong optBytesUsed = getCapacityUsedBytes();
        if (optLimitBytes.isPresent()) {
            if (optBytesUsed.isPresent()) {
                return OptionalDouble.of(optBytesUsed.getAsLong() / (double) optLimitBytes.getAsLong() * 100);
            } else {
                return OptionalDouble.empty();
            }
        } else {
            final OptionalLong optTotalBytes = getTotalCapacityBytes();
            if (optBytesUsed.isPresent() && optTotalBytes.isPresent()) {
                return OptionalDouble.of(optBytesUsed.getAsLong() / (double) optTotalBytes.getAsLong() * 100);
            } else {
                return OptionalDouble.empty();
            }
        }
    }

    /**
     * @return True if the total capacity and bytes used are known.
     */
    default boolean hasValidState() {
        return getCapacityUsedBytes().isPresent()
                && getTotalCapacityBytes().isPresent();
    }

    /**
     * Default implementation for determining whether this is full or not.
     *
     * @return True if the capacity state is known and either the amount used is
     * greater than the limit, the amount used is greater than or equal to
     * MAX_USED_FRACTION of the total capacity or the amount used is greater than
     * or equal to the total capacity minus HEADROOM_BYTES.
     */
    default boolean isFull() {
        // If we haven't established how many bytes are used on a volume then
        // assume it is not full (could be dangerous but worst case we will get
        // an IO error).
        if (hasValidState()) {
            @SuppressWarnings("OptionalGetWithoutIsPresent") // Asserted by hasValidState check above
            final long usedBytes = getCapacityUsedBytes().getAsLong();
            @SuppressWarnings("OptionalGetWithoutIsPresent") // Asserted by hasValidState check above
            final long totalBytes = getTotalCapacityBytes().getAsLong();

            // If a byte limit has been set then ensure it is less than the total
            // number of bytes on the volume and if it is return whether the number
            // of bytes used are greater than this limit.
            if (getCapacityLimitBytes().isPresent()) {
                final long limitBytes = getCapacityLimitBytes().getAsLong();
                final long effectiveUserLimit = Math.min(totalBytes, limitBytes);
                return usedBytes >= effectiveUserLimit;
            } else {
                // No byte limit has been set by the user so establish the maximum size
                // that we will allow.
                // Choose the higher limit of either the total storage minus 10Gb or 99%
                // of total storage.
                final long totalMinusFixedHeadroom = totalBytes - DEFAULT_HEADROOM_BYTES;
                final long scaledTotal = (long) (totalBytes - (totalBytes * DEFAULT_HEADROOM_FRACTION));
                final long effectiveLimit = Math.max(totalMinusFixedHeadroom, scaledTotal);

                return usedBytes >= effectiveLimit;
            }
        } else {
            // We don't know the capacity state so assume not full until we do know.
            return false;
        }
    }

    default String asString() {
        return "total: " +
                (getTotalCapacityBytes().isPresent()
                        ? getTotalCapacityBytes()
                        : "?") +
                ", used: " +
                (getCapacityUsedBytes().isPresent()
                        ? getCapacityUsedBytes()
                        : "?") +
                ", free: " +
                (getFreeCapacityBytes().isPresent()
                        ? getFreeCapacityBytes()
                        : "?") +
                ", limit: " +
                (getCapacityLimitBytes().isPresent()
                        ? getCapacityLimitBytes()
                        : "?");
    }
}
