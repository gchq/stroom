/*
 * Copyright 2016 Crown Copyright
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

package stroom.data.store.impl.fs.shared;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasAuditInfo;
import stroom.util.shared.HasCapacity;
import stroom.util.shared.HasIntegerId;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalLong;

/**
 * Some path on the network where we can store stuff.
 */
@JsonInclude(Include.NON_NULL)
public class FsVolume implements HasAuditInfo, HasIntegerId, HasCapacity {

    private static final long HEADROOM_BYTES = 10L * 1024L * 1024L * 1024L; // 10G
    private static final double MAX_USED_FRACTION = 0.99D;

    @JsonProperty
    private Integer id;
    @JsonProperty
    private Integer version;
    @JsonProperty
    private Long createTimeMs;
    @JsonProperty
    private String createUser;
    @JsonProperty
    private Long updateTimeMs;
    @JsonProperty
    private String updateUser;
    @JsonProperty
    private String path;
    @JsonProperty
    private VolumeUseStatus status;
    @JsonProperty
    private Long byteLimit;
    @JsonProperty
    private FsVolumeState volumeState;

    public FsVolume() {
        status = VolumeUseStatus.ACTIVE;
    }

    @JsonCreator
    public FsVolume(@JsonProperty("id") final Integer id,
                    @JsonProperty("version") final Integer version,
                    @JsonProperty("createTimeMs") final Long createTimeMs,
                    @JsonProperty("createUser") final String createUser,
                    @JsonProperty("updateTimeMs") final Long updateTimeMs,
                    @JsonProperty("updateUser") final String updateUser,
                    @JsonProperty("path") final String path,
                    @JsonProperty("status") final VolumeUseStatus status,
                    @JsonProperty("byteLimit") final Long byteLimit,
                    @JsonProperty("volumeState") final FsVolumeState volumeState) {
        this.id = id;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.createUser = createUser;
        this.updateTimeMs = updateTimeMs;
        this.updateUser = updateUser;
        this.path = path;
        this.status = status;
        this.byteLimit = byteLimit;
        this.volumeState = volumeState;
    }

    public static FsVolume create(final String path) {
        return create(path, null);
    }

    /**
     * Utility to create a volume.
     *
     * @param path to use
     * @return volume
     */
    public static FsVolume create(final String path, final FsVolumeState volumeState) {
        return create(path, volumeState, null);
    }

    /**
     * Utility to create a volume.
     *
     * @param path to use
     * @return volume
     */
    public static FsVolume create(final String path,
                                  final FsVolumeState volumeState,
                                  final Long byteLimit) {
        final FsVolume volume = new FsVolume();
        volume.setPath(path);
        volume.setVolumeState(volumeState);
        if (byteLimit != null) {
            volumeState.setBytesFree(byteLimit - volumeState.getBytesUsed());
        }
        volume.setByteLimit(byteLimit);
        return volume;
    }

    @Override
    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(final Integer version) {
        this.version = version;
    }

    @Override
    public Long getCreateTimeMs() {
        return createTimeMs;
    }

    public void setCreateTimeMs(final Long createTimeMs) {
        this.createTimeMs = createTimeMs;
    }

    @Override
    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(final String createUser) {
        this.createUser = createUser;
    }

    @Override
    public Long getUpdateTimeMs() {
        return updateTimeMs;
    }

    public void setUpdateTimeMs(final Long updateTimeMs) {
        this.updateTimeMs = updateTimeMs;
    }

    @Override
    public String getUpdateUser() {
        return updateUser;
    }

    public void setUpdateUser(final String updateUser) {
        this.updateUser = updateUser;
    }

    /**
     * @return The path the volume resides in. This may be a relative path in which case
     * it should be resolved relative to stroom.home before use.
     */
    public String getPath() {
        return path;
    }

    public void setPath(final String path) {
        this.path = path;
    }

    public VolumeUseStatus getStatus() {
        return status;
    }

    public void setStatus(final VolumeUseStatus status) {
        this.status = status;
    }

    public Long getByteLimit() {
        return byteLimit;
    }

    public void setByteLimit(final Long byteLimit) {
        this.byteLimit = byteLimit;
    }

    public FsVolumeState getVolumeState() {
        return volumeState;
    }

    public void setVolumeState(final FsVolumeState volumeState) {
        this.volumeState = volumeState;
    }

    @JsonIgnore
    public boolean isFull() {
        // If we haven't established how many bytes are used on a volume then
        // assume it is not full (could be dangerous but worst case we will get
        // an IO error).
        if (volumeState.getBytesUsed() == null || volumeState.getBytesTotal() == null) {
            return false;
        }

        final long used = volumeState.getBytesUsed();
        final long total = volumeState.getBytesTotal();

        // If a byte limit has been set then ensure it is less than the total
        // number of bytes on the volume and if it is return whether the number
        // of bytes used are greater than this limit.
        if (byteLimit != null && byteLimit < total) {
            return used >= byteLimit;
        }

        // No byte limit has been set by the user so establish the maximum size
        // that we will allow.
        // Choose the higher limit of either the total storage minus 10Gb or 99%
        // of total storage.
        final long totalMinusFixedHeadroom = total - HEADROOM_BYTES;
        final long scaledTotal = (long) (total * MAX_USED_FRACTION);
        final long maxUsed = Math.max(totalMinusFixedHeadroom, scaledTotal);

        return used >= maxUsed;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FsVolume that = (FsVolume) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(version, that.version) &&
                Objects.equals(createTimeMs, that.createTimeMs) &&
                Objects.equals(createUser, that.createUser) &&
                Objects.equals(updateTimeMs, that.updateTimeMs) &&
                Objects.equals(updateUser, that.updateUser) &&
                Objects.equals(path, that.path) &&
                status == that.status &&
                Objects.equals(byteLimit, that.byteLimit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version, createTimeMs, createUser, updateTimeMs, updateUser, path, status, byteLimit);
    }

    @Override
    public String toString() {
        return "FileSystemVolume{" +
                "path='" + path + '\'' +
                ", status=" + status +
                ", byteLimit=" + byteLimit +
                ", volumeState=" + volumeState +
                '}';
    }

    public FsVolume copy() {
        final FsVolume volume = new FsVolume();
        volume.path = path;
        volume.status = status;
        volume.byteLimit = byteLimit;
        volume.volumeState = volumeState;
        return volume;
    }

    @JsonIgnore
    @Override
    public OptionalLong getCapacityUsedBytes() {
        if (volumeState == null) {
            return OptionalLong.empty();
        } else {
            final Long bytesUsed = volumeState.getBytesUsed();
            return bytesUsed != null
                    ? OptionalLong.of(bytesUsed)
                    : OptionalLong.empty();
        }
    }

    @JsonIgnore
    @Override
    public OptionalLong getCapacityLimitBytes() {
        return byteLimit != null
                ? OptionalLong.of(byteLimit)
                : OptionalLong.empty();
    }

    @JsonIgnore
    @Override
    public OptionalLong getTotalCapacityBytes() {
        if (volumeState == null) {
            return OptionalLong.empty();
        } else {
            final Long bytesTotal = volumeState.getBytesTotal();
            return bytesTotal != null
                    ? OptionalLong.of(bytesTotal)
                    : OptionalLong.empty();
        }
    }

    @JsonIgnore
    @Override
    public OptionalLong getFreeCapacityBytes() {
        if (volumeState == null) {
            return OptionalLong.empty();
        } else {
            final Long bytesFree = volumeState.getBytesFree();
            return bytesFree != null
                    ? OptionalLong.of(bytesFree)
                    : OptionalLong.empty();
        }
    }

    @JsonIgnore
    @Override
    public OptionalDouble getFreeCapacityPercent() {
        if (volumeState == null) {
            return OptionalDouble.empty();
        } else {
            if (byteLimit != null) {
                final Long bytesUsed = volumeState.getBytesUsed();
                if (bytesUsed != null) {
                    return OptionalDouble.of((byteLimit - bytesUsed) / (double) byteLimit * 100);
                } else {
                    return OptionalDouble.empty();
                }
            } else {
                final Long bytesTotal = volumeState.getBytesTotal();
                final Long bytesFree = volumeState.getBytesFree();
                if (bytesFree != null && bytesTotal != null) {
                    return OptionalDouble.of(bytesFree / (double) bytesTotal * 100);
                } else {
                    return OptionalDouble.empty();
                }
            }
        }
    }

    public enum VolumeUseStatus implements HasDisplayValue, HasPrimitiveValue {
        ACTIVE("Active", 0), // Currently being written to.
        INACTIVE("Inactive", 1), // No longer being written to but still accessible for reading.
        CLOSED("Closed", 3); // Data has been removed and the volume is closed.

        public static final PrimitiveValueConverter<VolumeUseStatus> PRIMITIVE_VALUE_CONVERTER =
                new PrimitiveValueConverter<>(VolumeUseStatus.values());

        private final String displayValue;
        private final byte primitiveValue;

        VolumeUseStatus(final String displayValue, final int primitiveValue) {
            this.displayValue = displayValue;
            this.primitiveValue = (byte) primitiveValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }

        @Override
        public byte getPrimitiveValue() {
            return primitiveValue;
        }
    }
}
