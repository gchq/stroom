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

import stroom.util.shared.ModelStringUtil;

import javax.persistence.Transient;

/**
 * State of a volume.
 */
public class FileSystemVolumeState {
    public static final String ENTITY_TYPE = "VolumeState";
    private static final double ONE_HUNDRED = 100D;
    private Long bytesUsed;
    private Long bytesFree;
    private Long bytesTotal;
    private Long statusMs;

    public static FileSystemVolumeState create(final long bytesUsed, final long bytesTotal) {
        final FileSystemVolumeState state = new FileSystemVolumeState();
        state.setBytesUsed(bytesUsed);
        state.setBytesFree(bytesTotal - bytesUsed);
        state.setBytesTotal(bytesTotal);
        state.setStatusMs(System.currentTimeMillis());
        return state;
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

    @Transient
    public Long getPercentUsed() {
        Long percent = null;
        if (bytesUsed != null && bytesTotal != null) {
            percent = Double.valueOf(((double) bytesUsed) / ((double) bytesTotal) * ONE_HUNDRED).longValue();
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
