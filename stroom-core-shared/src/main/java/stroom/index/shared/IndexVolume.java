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

package stroom.index.shared;

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
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * Some path on the network where we can store stuff.
 */
@JsonPropertyOrder({
        "id",
        "version",
        "createTimeMs",
        "createUser",
        "updateTimeMs",
        "updateUser",
        "path",
        "nodeName",
        "state",
        "bytesLimit",
        "bytesUsed",
        "bytesFree",
        "bytesTotal",
        "statusMs",
        "indexVolumeGroupId"
})
@JsonInclude(Include.NON_NULL)
public class IndexVolume implements HasAuditInfo, HasIntegerId, HasCapacity {

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
    private String nodeName;
    @JsonProperty
    private VolumeUseState state = VolumeUseState.ACTIVE;
    @JsonProperty
    private Long bytesLimit;
    @JsonProperty
    private Long bytesUsed;
    @JsonProperty
    private Long bytesFree;
    @JsonProperty
    private Long bytesTotal;
    @JsonProperty
    private Long statusMs;
    @JsonProperty
    private Integer indexVolumeGroupId;

    @JsonIgnore
    private final HasCapacityInfo capacityInfo = new CapacityInfo();

    public IndexVolume() {
    }

    @JsonCreator
    public IndexVolume(@JsonProperty("id") final Integer id,
                       @JsonProperty("version") final Integer version,
                       @JsonProperty("createTimeMs") final Long createTimeMs,
                       @JsonProperty("createUser") final String createUser,
                       @JsonProperty("updateTimeMs") final Long updateTimeMs,
                       @JsonProperty("updateUser") final String updateUser,
                       @JsonProperty("path") final String path,
                       @JsonProperty("nodeName") final String nodeName,
                       @JsonProperty("state") final VolumeUseState state,
                       @JsonProperty("bytesLimit") final Long bytesLimit,
                       @JsonProperty("bytesUsed") final Long bytesUsed,
                       @JsonProperty("bytesFree") final Long bytesFree,
                       @JsonProperty("bytesTotal") final Long bytesTotal,
                       @JsonProperty("statusMs") final Long statusMs,
                       @JsonProperty("indexVolumeGroupId") final Integer indexVolumeGroupId) {
        this.id = id;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.createUser = createUser;
        this.updateTimeMs = updateTimeMs;
        this.updateUser = updateUser;
        this.path = path;
        this.nodeName = nodeName;
        this.state = state;
        this.bytesLimit = bytesLimit;
        this.bytesUsed = bytesUsed;
        this.bytesFree = bytesFree;
        this.bytesTotal = bytesTotal;
        this.statusMs = statusMs;
        this.indexVolumeGroupId = indexVolumeGroupId;
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

    public Integer getIndexVolumeGroupId() {
        return indexVolumeGroupId;
    }

    public void setIndexVolumeGroupId(final Integer indexVolumeGroupId) {
        this.indexVolumeGroupId = indexVolumeGroupId;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(final String nodeName) {
        this.nodeName = nodeName;
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

    public VolumeUseState getState() {
        return state;
    }

    public void setState(final VolumeUseState state) {
        this.state = state;
    }

    public Long getBytesLimit() {
        return bytesLimit;
    }

    public void setBytesLimit(final Long bytesLimit) {
        this.bytesLimit = bytesLimit;
    }

    public Long getBytesUsed() {
        return bytesUsed;
    }

    public void setBytesUsed(final Long bytesUsed) {
        this.bytesUsed = bytesUsed;
    }

    public Long getBytesFree() {
        return bytesFree;
    }

    public void setBytesFree(final Long bytesFree) {
        this.bytesFree = bytesFree;
    }

    public Long getBytesTotal() {
        return bytesTotal;
    }

    public void setBytesTotal(final Long bytesTotal) {
        this.bytesTotal = bytesTotal;
    }

    public Long getStatusMs() {
        return statusMs;
    }

    public void setStatusMs(final Long statusMs) {
        this.statusMs = statusMs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    @JsonIgnore
    @Override
    public HasCapacityInfo getCapacityInfo() {
        return capacityInfo;
    }

    @JsonIgnore
    @Override
    public String getIdentifier() {
        return nodeName + ":" + path;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final IndexVolume that = (IndexVolume) o;
        return Objects.equals(id, that.id) && Objects.equals(version,
                that.version) && Objects.equals(createTimeMs, that.createTimeMs) && Objects.equals(
                createUser,
                that.createUser) && Objects.equals(updateTimeMs, that.updateTimeMs) && Objects.equals(
                updateUser,
                that.updateUser) && Objects.equals(path, that.path) && Objects.equals(nodeName,
                that.nodeName) && state == that.state && Objects.equals(bytesLimit,
                that.bytesLimit) && Objects.equals(bytesUsed, that.bytesUsed) && Objects.equals(
                bytesFree,
                that.bytesFree) && Objects.equals(bytesTotal, that.bytesTotal) && Objects.equals(
                statusMs,
                that.statusMs) && Objects.equals(indexVolumeGroupId, that.indexVolumeGroupId);
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
                nodeName,
                state,
                bytesLimit,
                bytesUsed,
                bytesFree,
                bytesTotal,
                statusMs,
                indexVolumeGroupId);
    }

    public enum VolumeUseState implements HasDisplayValue, HasPrimitiveValue {
        ACTIVE("Active", 0), // Currently being written to.
        INACTIVE("Inactive", 1), // No longer being written to but still accessible for reading.
        CLOSED("Closed", 3); // Data has been removed and the volume is closed.

        public static final PrimitiveValueConverter<VolumeUseState> PRIMITIVE_VALUE_CONVERTER =
                PrimitiveValueConverter.create(VolumeUseState.class, VolumeUseState.values());

        private final String displayValue;
        private final byte primitiveValue;

        VolumeUseState(final String displayValue, final int primitiveValue) {
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

    public static final class Builder {

        private Integer id;
        private Integer version;
        private Long createTimeMs;
        private String createUser;
        private Long updateTimeMs;
        private String updateUser;
        private String path;
        private String nodeName;
        private VolumeUseState state = VolumeUseState.ACTIVE;
        private Long bytesLimit;
        private Long bytesUsed;
        private Long bytesFree;
        private Long bytesTotal;
        private Long statusMs;
        private Integer indexVolumeGroupId;

        private Builder() {
        }

        private Builder(final IndexVolume indexVolume) {
            this.id = indexVolume.id;
            this.version = indexVolume.version;
            this.createTimeMs = indexVolume.createTimeMs;
            this.createUser = indexVolume.createUser;
            this.updateTimeMs = indexVolume.updateTimeMs;
            this.updateUser = indexVolume.updateUser;
            this.path = indexVolume.path;
            this.nodeName = indexVolume.nodeName;
            this.state = indexVolume.state;
            this.bytesLimit = indexVolume.bytesLimit;
            this.bytesUsed = indexVolume.bytesUsed;
            this.bytesFree = indexVolume.bytesFree;
            this.bytesTotal = indexVolume.bytesTotal;
            this.statusMs = indexVolume.statusMs;
            this.indexVolumeGroupId = indexVolume.indexVolumeGroupId;
        }

        // Replaces the copy function
        public Builder fromOriginal(final IndexVolume original) {
            path = original.path;
            nodeName = original.nodeName;
            bytesLimit = original.bytesLimit;
            return this;
        }

        public Builder nodeName(final String nodeName) {
            this.nodeName = nodeName;
            return this;
        }

        public Builder path(final String path) {
            this.path = path;
            return this;
        }

        public Builder state(final VolumeUseState state) {
            this.state = state;
            return this;
        }

        public Builder bytesUsed(final Long bytesUsed) {
            this.bytesUsed = bytesUsed;
            return this;
        }

        public Builder bytesFree(final Long bytesFree) {
            this.bytesFree = bytesFree;
            return this;
        }

        public Builder bytesTotal(final Long bytesTotal) {
            this.bytesTotal = bytesTotal;
            return this;
        }

        public Builder bytesLimit(final Long bytesLimit) {
            this.bytesLimit = bytesLimit;
            return this;
        }

        public Builder statusMs(final Long statusMs) {
            this.statusMs = statusMs;
            return this;
        }

        public Builder indexVolumeGroupId(final Integer indexVolumeGroupId) {
            this.indexVolumeGroupId = indexVolumeGroupId;
            return this;
        }

        public IndexVolume build() {
            return new IndexVolume(
                    id,
                    version,
                    createTimeMs,
                    createUser,
                    updateTimeMs,
                    updateUser,
                    path,
                    nodeName,
                    state,
                    bytesLimit,
                    bytesUsed,
                    bytesFree,
                    bytesTotal,
                    statusMs,
                    indexVolumeGroupId);
        }
    }

    @Override
    public String toString() {
        return "IndexVolume{" +
               "id=" + id +
               ", path='" + path + '\'' +
               ", nodeName='" + nodeName + '\'' +
               ", state=" + state +
               ", indexVolumeGroupId=" + indexVolumeGroupId +
               '}';
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    /**
     * Essentially a read-only view of some parts of {@link IndexVolume}
     */
    private class CapacityInfo implements HasCapacityInfo {

        @Override
        public OptionalLong getCapacityUsedBytes() {
            return bytesUsed != null
                    ? OptionalLong.of(bytesUsed)
                    : OptionalLong.empty();
        }

        @Override
        public OptionalLong getCapacityLimitBytes() {
            return bytesLimit != null
                    ? OptionalLong.of(bytesLimit)
                    : OptionalLong.empty();
        }

        @Override
        public OptionalLong getTotalCapacityBytes() {
            return bytesTotal != null
                    ? OptionalLong.of(bytesTotal)
                    : OptionalLong.empty();
        }

        @Override
        public OptionalLong getFreeCapacityBytes() {
            return bytesFree != null
                    ? OptionalLong.of(bytesFree)
                    : OptionalLong.empty();
        }

        @Override
        public String toString() {
            return this.asString();
        }
    }
}
