/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.data.store.impl.fs.s3v2;


import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.meta.shared.Meta;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public record FileKey(
        int volumeId,
        long metaId,
        @NonNull String streamType,
        @Nullable String childStreamType) {

    public FileKey {
        if (metaId < 0) {
            throw new IllegalStateException("metaId must be non-negative");
        }
        Objects.requireNonNull(streamType, "streamType must not be null");
    }

    public static FileKey of(final int volumeId, final Meta meta, final String childStreamType) {
        Objects.requireNonNull(meta, "meta must not be null");
        return new FileKey(volumeId, meta.getId(), meta.getTypeName(), childStreamType);
    }

    public static FileKey of(final DataVolume dataVolume, final Meta meta, final String childStreamType) {
        Objects.requireNonNull(meta, "meta must not be null");
        Objects.requireNonNull(dataVolume, "dataVolume must not be null");
        return new FileKey(
                dataVolume.getVolumeId(),
                meta.getId(),
                meta.getTypeName(),
                childStreamType);
    }

    public static FileKey of(final int volumeId, final Meta meta) {
        return FileKey.of(volumeId, meta, null);
    }

    public static FileKey of(final DataVolume dataVolume, final Meta meta) {
        Objects.requireNonNull(dataVolume, "dataVolume must not be null");
        return FileKey.of(dataVolume.getVolumeId(), meta, null);
    }

    /**
     * Creates a clone of this with the supplied childStreamType.
     */
    public FileKey withChildStreamType(@Nullable final String childStreamType) {
        if (Objects.equals(childStreamType, this.childStreamType)) {
            return this;
        } else {
            return new FileKey(volumeId, metaId, streamType, childStreamType);
        }
    }
}
