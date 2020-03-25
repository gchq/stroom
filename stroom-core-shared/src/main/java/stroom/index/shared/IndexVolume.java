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

package stroom.index.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasAuditInfo;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

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
public class IndexVolume implements HasAuditInfo {
    private static final long TEN_GB = 10L * 1024L * 1024L * 1024L;
    private static final double NINETY_NINE_PERCENT = 0.99D;
    private static final double ONE_HUNDRED = 100D;

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

    public static class Builder {
        private final IndexVolume instance;

        public Builder(final IndexVolume instance) {
            this.instance = instance;
        }

        public Builder() {
            this(new IndexVolume());
        }

        // Replaces the copy function
        public Builder fromOriginal(final IndexVolume original) {
            instance.path = original.path;
            instance.nodeName = original.nodeName;
            instance.bytesLimit = original.bytesLimit;
            return this;
        }

        public Builder nodeName(final String value) {
            instance.setNodeName(value);
            return this;
        }

        public Builder path(final String value) {
            instance.setPath(value);
            return this;
        }

        public Builder status(final VolumeUseState value) {
            instance.setState(value);
            return this;
        }

        public Builder bytesUsed(final Long value) {
            instance.setBytesUsed(value);
            return this;
        }

        public Builder bytesFree(final Long value) {
            instance.setBytesFree(value);
            return this;
        }

        public Builder bytesTotal(final Long value) {
            instance.setBytesTotal(value);
            return this;
        }

        public Builder statusMs(final Long value) {
            instance.setStatusMs(value);
            return this;
        }

        public Builder indexVolumeGroupId(final Integer indexVolumeGroupId) {
            instance.setIndexVolumeGroupId(indexVolumeGroupId);
            return this;
        }

        public IndexVolume build() {
            return instance;
        }
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

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

    @JsonIgnore
    public boolean isFull() {
        // If we haven't established how many bytes are used on a volume then
        // assume it is not full (could be dangerous but worst case we will get
        // an IO error).
        if (bytesUsed == null || bytesTotal == null) {
            return false;
        }

        // If a byte limit has been set then ensure it is less than the total
        // number of bytes on the volume and if it is return whether the number
        // of bytes used are greater than this limit.
        if (bytesLimit != null && bytesLimit < bytesTotal) {
            return bytesUsed >= bytesLimit;
        }

        // No byte limit has been set by the user so establish the maximum size
        // that we will allow.
        // Choose the higher limit of either the total storage minus 10Gb or 99%
        // of total storage.
        final long minusTenGig = bytesTotal - TEN_GB;
        final long percentage = (long) (bytesTotal * NINETY_NINE_PERCENT);
        final long max = Math.max(minusTenGig, percentage);

        return bytesUsed >= max;
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

    @JsonIgnore
    public Long getPercentUsed() {
        Long percent = null;
        if (bytesUsed != null && bytesTotal != null) {
            percent = Double.valueOf(((double) bytesUsed) / ((double) bytesTotal) * ONE_HUNDRED).longValue();
        }
        return percent;
    }

    public enum VolumeUseState implements HasDisplayValue, HasPrimitiveValue {
        ACTIVE("Active", 0), // Currently being written to.
        INACTIVE("Inactive", 1), // No longer being written to but still accessible for reading.
        CLOSED("Closed", 3); // Data has been removed and the volume is closed.

        public static final PrimitiveValueConverter<VolumeUseState> PRIMITIVE_VALUE_CONVERTER = new PrimitiveValueConverter<>(
                VolumeUseState.values());

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
}
