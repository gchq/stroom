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
import stroom.docref.SharedObject;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;
import stroom.util.shared.HasAuditInfo;

import javax.persistence.Transient;

/**
 * Some path on the network where we can store stuff.
 */
public class FileSystemVolume implements HasAuditInfo, SharedObject {
    private static final long TEN_GB = 10 * 1024 * 1024 * 1024;
    private static final double NINETY_NINE_PERCENT = 0.99D;

    private Integer id;
    private Integer version;
    private Long createTimeMs;
    private String createUser;
    private Long updateTimeMs;
    private String updateUser;
    private String path;
    private VolumeUseStatus status = VolumeUseStatus.ACTIVE;
    private Long bytesLimit;
    private FileSystemVolumeState volumeState;

    public FileSystemVolume() {
    }

    public static FileSystemVolume create(final String path) {
        return create(path, null);
    }

    /**
     * Utility to create a volume.
     *
     * @param node to use
     * @param path to use
     * @return volume
     */
    public static FileSystemVolume create(final String path, final FileSystemVolumeState volumeState) {
        final FileSystemVolume volume = new FileSystemVolume();
        volume.setPath(path);
        volume.setVolumeState(volumeState);
        return volume;
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

    public Long getBytesLimit() {
        return bytesLimit;
    }

    public void setBytesLimit(final Long bytesLimit) {
        this.bytesLimit = bytesLimit;
    }

    public FileSystemVolumeState getVolumeState() {
        return volumeState;
    }

    public void setVolumeState(final FileSystemVolumeState volumeState) {
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
    public String toString() {
        return "FileSystemVolume{" +
                "path='" + path + '\'' +
                ", status=" + status +
                ", bytesLimit=" + bytesLimit +
                ", volumeState=" + volumeState +
                '}';
    }

    public FileSystemVolume copy() {
        final FileSystemVolume volume = new FileSystemVolume();
        volume.path = path;
        volume.status = status;
        volume.bytesLimit = bytesLimit;
        volume.volumeState = volumeState;
        return volume;
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
