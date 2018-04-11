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

import stroom.entity.shared.AuditedEntity;
import stroom.entity.shared.HasPrimitiveValue;
import stroom.entity.shared.LengthConstants;
import stroom.entity.shared.PrimitiveValueConverter;
import stroom.entity.shared.SQLNameConstants;
import stroom.util.shared.HasDisplayValue;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.Size;

/**
 * Some path on the network where we can store stuff.
 */
@Entity
@Table(name = "VOL", uniqueConstraints = @UniqueConstraint(columnNames = {"FK_ND_ID", "PATH"}))
public class Volume extends AuditedEntity {
    public static final String TABLE_NAME = SQLNameConstants.VOLUME;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String PATH = SQLNameConstants.PATH;
    public static final String VOLUME_TYPE = TABLE_NAME + SQLNameConstants.TYPE_SUFFIX;
    public static final String STREAM_STATUS = SQLNameConstants.STREAM + SQLNameConstants.STATUS_SUFFIX;
    public static final String INDEX_STATUS = SQLNameConstants.INDEX + SQLNameConstants.STATUS_SUFFIX;
    public static final String BYTES_LIMIT = SQLNameConstants.BYTES + SQLNameConstants.LIMIT_SUFFIX;
    public static final String ENTITY_TYPE = "Volume";

    private static final long serialVersionUID = 2100406585501252906L;
    private static final long TEN_GB = 10 * 1024 * 1024 * 1024;
    private static final double NINETY_NINE_PERCENT = 0.99D;

    private String path;
    private byte pvolumeType = VolumeType.PUBLIC.getPrimitiveValue();
    private byte pstreamStatus = VolumeUseStatus.ACTIVE.getPrimitiveValue();
    private byte pindexStatus = VolumeUseStatus.ACTIVE.getPrimitiveValue();
    private Node node;
    private Long bytesLimit;
    private VolumeState volumeState;

    public Volume() {
    }

    public static Volume create(final Node node, final String path, final VolumeType volumeType) {
        return create(node, path, volumeType, null);
    }

    /**
     * Utility to create a volume.
     *
     * @param node       to use
     * @param path       to use
     * @param volumeType to use
     * @return volume
     */
    public static Volume create(final Node node, final String path, final VolumeType volumeType,
                                final VolumeState volumeState) {
        final Volume volume = new Volume();
        volume.setNode(node);
        volume.setPath(path);
        volume.setVolumeType(volumeType);
        volume.setVolumeState(volumeState);
        return volume;
    }

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = Node.FOREIGN_KEY)
    public Node getNode() {
        return node;
    }

    public void setNode(final Node node) {
        this.node = node;
    }

    @Column(name = PATH, nullable = false)
    @Size(min = LengthConstants.MIN_NAME_LENGTH)
    public String getPath() {
        return path;
    }

    public void setPath(final String path) {
        this.path = path;
    }

    @Column(name = VOLUME_TYPE, nullable = false)
    public byte getPvolumeType() {
        return pvolumeType;
    }

    public void setPvolumeType(final byte pVolumeType) {
        this.pvolumeType = pVolumeType;
    }

    @Transient
    public VolumeType getVolumeType() {
        return VolumeType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(pvolumeType);
    }

    public void setVolumeType(final VolumeType volumeType) {
        this.pvolumeType = volumeType.getPrimitiveValue();
    }

    @Column(name = STREAM_STATUS, nullable = false)
    public byte getPstreamStatus() {
        return pstreamStatus;
    }

    public void setPstreamStatus(final byte pStoreStatus) {
        this.pstreamStatus = pStoreStatus;
    }

    @Transient
    public VolumeUseStatus getStreamStatus() {
        return VolumeUseStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(pstreamStatus);
    }

    public void setStreamStatus(final VolumeUseStatus streamStatus) {
        this.pstreamStatus = streamStatus.getPrimitiveValue();
    }

    @Column(name = INDEX_STATUS, nullable = false)
    public byte getPindexStatus() {
        return pindexStatus;
    }

    public void setPindexStatus(final byte pIndexStatus) {
        this.pindexStatus = pIndexStatus;
    }

    @Transient
    public VolumeUseStatus getIndexStatus() {
        return VolumeUseStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(pindexStatus);
    }

    public void setIndexStatus(final VolumeUseStatus indexStatus) {
        this.pindexStatus = indexStatus.getPrimitiveValue();
    }

    @Column(name = BYTES_LIMIT, columnDefinition = BIGINT_UNSIGNED)
    public Long getBytesLimit() {
        return bytesLimit;
    }

    public void setBytesLimit(final Long bytesLimit) {
        this.bytesLimit = bytesLimit;
    }

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = VolumeState.FOREIGN_KEY)
    public VolumeState getVolumeState() {
        return volumeState;
    }

    public void setVolumeState(final VolumeState volumeState) {
        this.volumeState = volumeState;
    }

    @Transient
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
        if (bytesLimit != null && bytesLimit < total) {
            return volumeState.getBytesUsed() >= bytesLimit;
        }

        // No byte limit has been set by the user so establish the maximum size
        // that we will allow.
        // Choose the higher limit of either the total storage minus 10Gb or 99%
        // of total storage.
        final long minusOneGig = total - TEN_GB;
        final long percentage = (long) (total * NINETY_NINE_PERCENT);
        final long max = Math.max(minusOneGig, percentage);

        return used >= max;
    }

    @Override
    protected void toString(final StringBuilder sb) {
        super.toString(sb);
        if (node != null) {
            sb.append(", node=");
            sb.append(node.getName());
        }
        if (getVolumeType() != null) {
            sb.append(", volumeType=");
            sb.append(getVolumeType().getDisplayValue());
        }
        if (path != null) {
            sb.append(", path=");
            sb.append(path);
        }
        sb.append(" (");
        if (volumeState != null) {
            volumeState.toString(sb);
        } else {
            sb.append("Unknown volume state");
        }
        sb.append(")");
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }

    public Volume copy() {
        final Volume volume = new Volume();
        volume.path = path;
        volume.pvolumeType = pvolumeType;
        volume.pstreamStatus = pstreamStatus;
        volume.pindexStatus = pindexStatus;
        volume.node = node;
        volume.bytesLimit = bytesLimit;
        volume.volumeState = volumeState;
        return volume;
    }

    /**
     * A non generic class!
     */
    public enum VolumeType implements HasDisplayValue, HasPrimitiveValue {
        PUBLIC("Public", 0), PRIVATE("Private", 1);

        public static final PrimitiveValueConverter<VolumeType> PRIMITIVE_VALUE_CONVERTER = new PrimitiveValueConverter<>(
                VolumeType.values());
        private final String displayValue;
        private final byte primitiveValue;

        VolumeType(final String displayValue, final int primitiveValue) {
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

    public enum VolumeUseStatus implements HasDisplayValue, HasPrimitiveValue {
        ACTIVE("Active", 0), // Currently being written to.
        INACTIVE("Inactive", 1), // No longer being written to but still
        // accessible for reading.
        CLOSED("Closed", 3); // Data has been removed and the volume is closed.

        public static final PrimitiveValueConverter<VolumeUseStatus> PRIMITIVE_VALUE_CONVERTER = new PrimitiveValueConverter<>(
                VolumeUseStatus.values());

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
