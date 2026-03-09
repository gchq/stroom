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

import stroom.util.shared.AbstractBuilder;
import stroom.util.shared.ModelStringUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * State of a volume.
 */
@JsonInclude(Include.NON_NULL)
public class FsVolumeState {

    public static final String ENTITY_TYPE = "VolumeState";

    @JsonProperty
    private final int id;
    @JsonProperty
    private final int version;
    @JsonProperty
    private final Long bytesUsed;
    @JsonProperty
    private final Long bytesFree;
    @JsonProperty
    private final Long bytesTotal;
    @JsonProperty
    private final Long updateTimeMs;

    public static FsVolumeState create(final long bytesUsed, final long bytesTotal) {
        return builder()
                .bytesUsed(bytesUsed)
                .bytesFree(bytesTotal - bytesUsed)
                .bytesTotal(bytesTotal)
                .updateTimeMs(System.currentTimeMillis())
                .build();
    }

    @JsonCreator
    public FsVolumeState(@JsonProperty("id") final int id,
                         @JsonProperty("version") final int version,
                         @JsonProperty("bytesUsed") final Long bytesUsed,
                         @JsonProperty("bytesFree") final Long bytesFree,
                         @JsonProperty("bytesTotal") final Long bytesTotal,
                         @JsonProperty("updateTimeMs") final Long updateTimeMs) {
        this.id = id;
        this.version = version;
        this.bytesUsed = bytesUsed;
        this.bytesFree = bytesFree;
        this.bytesTotal = bytesTotal;
        this.updateTimeMs = updateTimeMs;
    }

    public int getId() {
        return id;
    }

    public int getVersion() {
        return version;
    }

    public Long getBytesUsed() {
        return bytesUsed;
    }

    /**
     * @return The number of bytes free for use, relative to the limit set or the total bytes if no limit is set.
     */
    public Long getBytesFree() {
        return bytesFree;
    }

    public Long getBytesTotal() {
        return bytesTotal;
    }

    public Long getUpdateTimeMs() {
        return updateTimeMs;
    }

    @JsonIgnore
    public Long getPercentUsed() {
        Long percent = null;
        if (bytesUsed != null && bytesTotal != null) {
            percent = Double.valueOf(((double) bytesUsed) / ((double) bytesTotal) * 100D).longValue();
        }
        return percent;
    }

    @Override
    public String toString() {
        final Long percentUsed = getPercentUsed();

        final StringBuilder sb = new StringBuilder();
        if (bytesUsed != null) {
            sb.append(", Used: ");
            sb.append(ModelStringUtil.formatIECByteSizeString(bytesUsed));
        }
        if (bytesFree != null) {
            sb.append(", Free: ");
            sb.append(ModelStringUtil.formatIECByteSizeString(bytesFree));
        }
        if (bytesTotal != null) {
            sb.append(", Total: ");
            sb.append(ModelStringUtil.formatIECByteSizeString(bytesTotal));
        }
        if (percentUsed != null) {
            sb.append(", Use%: ");
            sb.append(percentUsed);
            sb.append("%");
        }

        return sb.toString();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static class Builder extends AbstractBuilder<FsVolumeState, Builder> {

        private int id;
        private int version;
        private Long bytesUsed;
        private Long bytesFree;
        private Long bytesTotal;
        private Long updateTimeMs;

        private Builder() {
        }

        private Builder(final FsVolumeState fsVolumeState) {
            this.id = fsVolumeState.id;
            this.version = fsVolumeState.version;
            this.bytesUsed = fsVolumeState.bytesUsed;
            this.bytesFree = fsVolumeState.bytesFree;
            this.bytesTotal = fsVolumeState.bytesTotal;
            this.updateTimeMs = fsVolumeState.updateTimeMs;
        }

        public Builder id(final int id) {
            this.id = id;
            return self();
        }

        public Builder version(final int version) {
            this.version = version;
            return self();
        }

        public Builder bytesUsed(final Long bytesUsed) {
            this.bytesUsed = bytesUsed;
            return self();
        }

        public Builder bytesFree(final Long bytesFree) {
            this.bytesFree = bytesFree;
            return self();
        }

        public Builder bytesTotal(final Long bytesTotal) {
            this.bytesTotal = bytesTotal;
            return self();
        }

        public Builder updateTimeMs(final Long updateTimeMs) {
            this.updateTimeMs = updateTimeMs;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public FsVolumeState build() {
            return new FsVolumeState(
                    id,
                    version,
                    bytesUsed,
                    bytesFree,
                    bytesTotal,
                    updateTimeMs);
        }
    }
}
