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

package stroom.node.shared;

import stroom.entity.shared.BaseEntitySmall;
import stroom.entity.shared.SQLNameConstants;
import stroom.util.shared.ModelStringUtil;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;

/**
 * State of a volume.
 */
@Entity(name = "VOL_STATE")
public class VolumeState extends BaseEntitySmall {
    public static final String TABLE_NAME = SQLNameConstants.VOLUME + SEP + SQLNameConstants.STATE;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String BYTES_USED = SQLNameConstants.BYTES + SEP + SQLNameConstants.USED;
    public static final String BYTES_FREE = SQLNameConstants.BYTES + SEP + SQLNameConstants.FREE;
    public static final String BYTES_TOTAL = SQLNameConstants.BYTES + SEP + SQLNameConstants.TOTAL;
    public static final String STATUS_MS = SQLNameConstants.STATUS_MS;
    public static final String ENTITY_TYPE = "VolumeState";
    private static final long serialVersionUID = 5718264595570654559L;
    private static final double ONE_HUNDRED = 100D;
    private Long bytesUsed;
    private Long bytesFree;
    private Long bytesTotal;
    private Long statusMs;

    public static VolumeState create(final long bytesUsed, final long bytesTotal) {
        final VolumeState state = new VolumeState();
        state.setBytesUsed(bytesUsed);
        state.setBytesFree(bytesTotal - bytesUsed);
        state.setBytesTotal(bytesTotal);
        state.setStatusMs(System.currentTimeMillis());
        return state;
    }

    @Column(name = BYTES_USED, columnDefinition = BIGINT_UNSIGNED)
    public Long getBytesUsed() {
        return bytesUsed;
    }

    public void setBytesUsed(final Long bytesUsed) {
        this.bytesUsed = bytesUsed;
    }

    @Column(name = BYTES_FREE, columnDefinition = BIGINT_UNSIGNED)
    public Long getBytesFree() {
        return bytesFree;
    }

    public void setBytesFree(final Long bytesFree) {
        this.bytesFree = bytesFree;
    }

    @Column(name = BYTES_TOTAL, columnDefinition = BIGINT_UNSIGNED)
    public Long getBytesTotal() {
        return bytesTotal;
    }

    public void setBytesTotal(final Long bytesTotal) {
        this.bytesTotal = bytesTotal;
    }

    @Column(name = STATUS_MS, columnDefinition = BIGINT_UNSIGNED)
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
    protected void toString(final StringBuilder other) {
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

        if (sb.length() > 0) {
            other.append(sb.substring(2));
        }
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }
}
