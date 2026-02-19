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
import stroom.util.shared.AbstractBuilder;
import stroom.util.shared.HasAuditInfoBuilder;
import stroom.util.shared.HasAuditInfoGetters;
import stroom.util.shared.HasCapacity;
import stroom.util.shared.HasCapacityInfo;
import stroom.util.shared.HasIntegerId;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.NullSafe;
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
public class FsVolume implements HasAuditInfoGetters, HasIntegerId, HasCapacity {

    @JsonProperty
    private final Integer id;
    @JsonProperty
    private final Integer version;
    @JsonProperty
    private final Long createTimeMs;
    @JsonProperty
    private final String createUser;
    @JsonProperty
    private final Long updateTimeMs;
    @JsonProperty
    private final String updateUser;
    @JsonProperty
    private final String path;
    @JsonProperty
    private final VolumeUseStatus status;
    @JsonProperty
    private final Long byteLimit;
    @JsonProperty
    private final FsVolumeState volumeState;
    @JsonProperty
    private final FsVolumeType volumeType;
    @JsonProperty
    private final S3ClientConfig s3ClientConfig;
    @JsonProperty
    private final String s3ClientConfigData;
    @JsonProperty
    private final Integer volumeGroupId;

    @JsonIgnore
    private final HasCapacityInfo capacityInfo = new CapacityInfo();

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
        this.volumeType = NullSafe.requireNonNullElse(volumeType, FsVolumeType.STANDARD);
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
        FsVolumeState vs = volumeState;
        if (byteLimit != null) {
            vs = vs.copy().bytesFree(byteLimit - volumeState.getBytesUsed()).build();
        }
        return FsVolume
                .builder()
                .path(path)
                .volumeState(vs)
                .byteLimit(byteLimit)
                .build();
    }

    @Override
    public Integer getId() {
        return id;
    }

    public Integer getVersion() {
        return version;
    }

    @Override
    public Long getCreateTimeMs() {
        return createTimeMs;
    }

    @Override
    public String getCreateUser() {
        return createUser;
    }

    @Override
    public Long getUpdateTimeMs() {
        return updateTimeMs;
    }

    @Override
    public String getUpdateUser() {
        return updateUser;
    }

    /**
     * @return The path the volume resides in. This may be a relative path in which case
     * it should be resolved relative to stroom.home before use.
     */
    public String getPath() {
        return path;
    }

    public VolumeUseStatus getStatus() {
        return status;
    }

    public Long getByteLimit() {
        return byteLimit;
    }

    public FsVolumeState getVolumeState() {
        return volumeState;
    }

    public FsVolumeType getVolumeType() {
        return volumeType;
    }

    public Integer getVolumeGroupId() {
        return volumeGroupId;
    }

    public S3ClientConfig getS3ClientConfig() {
        return s3ClientConfig;
    }

    public String getS3ClientConfigData() {
        return s3ClientConfigData;
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


    public FsVolume duplicate() {
        return FsVolume
                .builder()
                .path(path)
                .status(status)
                .byteLimit(byteLimit)
                .volumeState(volumeState)
                .volumeType(volumeType)
                .s3ClientConfig(s3ClientConfig)
                .s3ClientConfigData(s3ClientConfigData)
                .volumeGroupId(volumeGroupId)
                .build();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static class Builder
            extends AbstractBuilder<FsVolume, Builder>
            implements HasAuditInfoBuilder<FsVolume, Builder> {

        private Integer id;
        private Integer version;
        private Long createTimeMs;
        private String createUser;
        private Long updateTimeMs;
        private String updateUser;
        private String path;
        private VolumeUseStatus status = VolumeUseStatus.ACTIVE;
        private Long byteLimit;
        private FsVolumeState volumeState;
        private FsVolumeType volumeType = FsVolumeType.STANDARD;
        private S3ClientConfig s3ClientConfig;
        private String s3ClientConfigData;
        private Integer volumeGroupId;

        private Builder() {
        }

        private Builder(final FsVolume fsVolume) {
            this.id = fsVolume.id;
            this.version = fsVolume.version;
            this.createTimeMs = fsVolume.createTimeMs;
            this.createUser = fsVolume.createUser;
            this.updateTimeMs = fsVolume.updateTimeMs;
            this.updateUser = fsVolume.updateUser;
            this.path = fsVolume.path;
            this.status = fsVolume.status;
            this.byteLimit = fsVolume.byteLimit;
            this.volumeState = fsVolume.volumeState;
            this.volumeType = fsVolume.volumeType;
            this.s3ClientConfig = fsVolume.s3ClientConfig;
            this.s3ClientConfigData = fsVolume.s3ClientConfigData;
            this.volumeGroupId = fsVolume.volumeGroupId;
        }

        public Builder id(final Integer id) {
            this.id = id;
            return self();
        }

        public Builder version(final Integer version) {
            this.version = version;
            return self();
        }

        @Override
        public Builder createTimeMs(final Long createTimeMs) {
            this.createTimeMs = createTimeMs;
            return self();
        }

        @Override
        public Builder createUser(final String createUser) {
            this.createUser = createUser;
            return self();
        }

        @Override
        public Builder updateTimeMs(final Long updateTimeMs) {
            this.updateTimeMs = updateTimeMs;
            return self();
        }

        @Override
        public Builder updateUser(final String updateUser) {
            this.updateUser = updateUser;
            return self();
        }

        public Builder path(final String path) {
            this.path = path;
            return self();
        }

        public Builder status(final VolumeUseStatus status) {
            this.status = status;
            return self();
        }

        public Builder byteLimit(final Long byteLimit) {
            this.byteLimit = byteLimit;
            return self();
        }

        public Builder volumeState(final FsVolumeState volumeState) {
            this.volumeState = volumeState;
            return self();
        }

        public Builder volumeType(final FsVolumeType volumeType) {
            this.volumeType = volumeType;
            return self();
        }

        public Builder s3ClientConfig(final S3ClientConfig s3ClientConfig) {
            this.s3ClientConfig = s3ClientConfig;
            return self();
        }

        public Builder s3ClientConfigData(final String s3ClientConfigData) {
            this.s3ClientConfigData = s3ClientConfigData;
            return self();
        }

        public Builder volumeGroupId(final Integer volumeGroupId) {
            this.volumeGroupId = volumeGroupId;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public FsVolume build() {
            return new FsVolume(
                    id,
                    version,
                    createTimeMs,
                    createUser,
                    updateTimeMs,
                    updateUser,
                    path,
                    status,
                    byteLimit,
                    volumeState,
                    volumeType,
                    s3ClientConfig,
                    s3ClientConfigData,
                    volumeGroupId);
        }
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
