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

import com.fasterxml.jackson.annotation.JsonIgnore;
import stroom.util.shared.ModelStringUtil;

/**
 * State of a volume.
 */
public class FsVolumeState {
    public static final String ENTITY_TYPE = "VolumeState";
    private int id;
    private int version;
    private Long bytesUsed;
    private Long bytesFree;
    private Long bytesTotal;
    private Long updateTimeMs;

    public static FsVolumeState create(final long bytesUsed, final long bytesTotal) {
        final FsVolumeState state = new FsVolumeState();
        state.setBytesUsed(bytesUsed);
        state.setBytesFree(bytesTotal - bytesUsed);
        state.setBytesTotal(bytesTotal);
        state.setUpdateTimeMs(System.currentTimeMillis());
        return state;
    }

    public FsVolumeState() {
    }

    public FsVolumeState(final int id, final int version, final Long bytesUsed, final Long bytesFree, final Long bytesTotal, final Long updateTimeMs) {
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

    public void setId(final int id) {
        this.id = id;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(final int version) {
        this.version = version;
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

    public Long getUpdateTimeMs() {
        return updateTimeMs;
    }

    public void setUpdateTimeMs(final Long updateTimeMs) {
        this.updateTimeMs = updateTimeMs;
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
}
