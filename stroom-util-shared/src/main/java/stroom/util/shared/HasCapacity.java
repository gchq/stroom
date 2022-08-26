package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.OptionalDouble;
import java.util.OptionalLong;

public interface HasCapacity {

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
}
