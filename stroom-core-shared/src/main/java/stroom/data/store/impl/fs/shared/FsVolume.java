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

package stroom.data.store.impl.fs.shared;

import stroom.aws.s3.shared.S3ClientConfig;
import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasAuditInfo;
import stroom.util.shared.HasCapacity;
import stroom.util.shared.HasCapacityInfo;
import stroom.util.shared.HasIntegerId;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * Some path on the network where we can store stuff.
 */
@JsonInclude(Include.NON_NULL)
public class FsVolume implements HasAuditInfo, HasIntegerId, HasCapacity {

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
    @JsonProperty
    private FsVolumeType volumeType;
    @JsonProperty
    private S3ClientConfig s3ClientConfig;
    @JsonProperty
    private String s3ClientConfigData;
    @JsonProperty
    private Integer volumeGroupId;

    @JsonIgnore
    private final HasCapacityInfo capacityInfo = new CapacityInfo();

    public FsVolume() {
        status = VolumeUseStatus.ACTIVE;
        volumeType = FsVolumeType.STANDARD;
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
                    @JsonProperty("volumeState") final FsVolumeState volumeState,
                    @JsonProperty("volumeType") final FsVolumeType volumeType,
                    @JsonProperty("s3ClientConfig") final S3ClientConfig s3ClientConfig,
                    @JsonProperty("s3ClientConfigData") final String s3ClientConfigData,
                    @JsonProperty("volumeGroupId") final Integer volumeGroupId) {
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
        this.volumeType = volumeType == null
                ? FsVolumeType.STANDARD
                : volumeType;
        this.s3ClientConfig = s3ClientConfig;
        this.s3ClientConfigData = s3ClientConfigData;
        this.volumeGroupId = volumeGroupId;
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

    public FsVolumeType getVolumeType() {
        return volumeType;
    }

    public void setVolumeType(final FsVolumeType volumeType) {
        this.volumeType = volumeType;
    }

    public Integer getVolumeGroupId() {
        return volumeGroupId;
    }

    public void setVolumeGroupId(final Integer volumeGroupId) {
        this.volumeGroupId = volumeGroupId;
    }

    public S3ClientConfig getS3ClientConfig() {
        return s3ClientConfig;
    }

    public void setS3ClientConfig(final S3ClientConfig s3ClientConfig) {
        this.s3ClientConfig = s3ClientConfig;
    }

    public String getS3ClientConfigData() {
        return s3ClientConfigData;
    }

    public void setS3ClientConfigData(final String s3ClientConfigData) {
        this.s3ClientConfigData = s3ClientConfigData;
    }

    @JsonIgnore
    @Override
    public HasCapacityInfo getCapacityInfo() {
        return capacityInfo;
    }

    @JsonIgnore
    @Override
    public String getIdentifier() {
        return path;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FsVolume volume = (FsVolume) o;
        return Objects.equals(id, volume.id) &&
               Objects.equals(version, volume.version) &&
               Objects.equals(createTimeMs, volume.createTimeMs) &&
               Objects.equals(createUser, volume.createUser) &&
               Objects.equals(updateTimeMs, volume.updateTimeMs) &&
               Objects.equals(updateUser, volume.updateUser) &&
               Objects.equals(path, volume.path) &&
               status == volume.status &&
               Objects.equals(byteLimit, volume.byteLimit) &&
               volumeType == volume.volumeType &&
               Objects.equals(s3ClientConfig, volume.s3ClientConfig) &&
               Objects.equals(s3ClientConfigData, volume.s3ClientConfigData) &&
               Objects.equals(volumeGroupId, volume.volumeGroupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id,
                version,
                createTimeMs,
                createUser,
                updateTimeMs,
                updateUser,
                path,
                status,
                byteLimit,
                volumeType,
                s3ClientConfig,
                s3ClientConfigData,
                volumeGroupId);
    }


    public FsVolume copy() {
        final FsVolume volume = new FsVolume();
        volume.path = path;
        volume.status = status;
        volume.byteLimit = byteLimit;
        volume.volumeState = volumeState;
        volume.volumeType = volumeType;
        volume.s3ClientConfig = s3ClientConfig;
        volume.s3ClientConfigData = s3ClientConfigData;
        volume.volumeGroupId = volumeGroupId;
        return volume;
    }


    // --------------------------------------------------------------------------------


    public enum VolumeUseStatus implements HasDisplayValue, HasPrimitiveValue {
        ACTIVE("Active", 0), // Currently being written to.
        INACTIVE("Inactive", 1), // No longer being written to but still accessible for reading.
        CLOSED("Closed", 3); // Data has been removed and the volume is closed.

        public static final PrimitiveValueConverter<VolumeUseStatus> PRIMITIVE_VALUE_CONVERTER =
                PrimitiveValueConverter.create(VolumeUseStatus.class, VolumeUseStatus.values());

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

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    /**
     * Essentially a read-only view of some parts of {@link FsVolume}
     */
    private class CapacityInfo implements HasCapacityInfo {

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

        @Override
        public OptionalLong getCapacityLimitBytes() {
            return byteLimit != null
                    ? OptionalLong.of(byteLimit)
                    : OptionalLong.empty();
        }

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

        @Override
        public String toString() {
            return this.asString();
        }
    }
}
