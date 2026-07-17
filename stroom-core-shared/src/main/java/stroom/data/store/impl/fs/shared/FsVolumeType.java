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

package stroom.data.store.impl.fs.shared;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum FsVolumeType implements HasDisplayValue, HasPrimitiveValue {

    // ************ IMPORTANT!!!!!! ************
    // Do NOT change the id values unless you fully understand the consequences.
    // They are stored in the DB, so changing them will impact the volume type
    // used for existing volumes, and likely break things.
    // ************ IMPORTANT!!!!!! ************

    /**
     * Uses a posix file system that is mounted locally.
     */
    STANDARD(0, "Standard", false),

    /**
     * The first iteration of an S3 stream store implementation.
     * Writes all files (dat/meta/ctx/dat.idx/mf/etc) un-compressed to a temp dir, zips them up
     * then uploads the file to S3.
     * Reads are done by downloading the zip file from S3 to a temp dir and unzipping it.
     */
    S3_V1(1, "S3 v1", false),

    /**
     * The second iteration of an S3 stream store implementation.
     * Writes all the data to a temp dir. Data is compressed with zstd. Each segment is compressed
     * as a separate zstd frame. The segment index is included at the end of the zstd file as a skippable frame.
     * If there is sufficient data to create a dictionary, then a dictionary will be used.
     */
    S3_V2(2, "S3 v2 (Experimental)", false),

    /**
     * A read only implementation of S3_V1.
     * Intended for use where the raw data is uploaded to S3 by proxy (in proxy zip format)
     * and read directly from there.
     */
    S3_V1_READ_ONLY(3, "S3 v1 (Read only)", true),

    ;

    /**
     * Volume types that use the S3 storage platform.
     */
    private static final Set<FsVolumeType> S3_VOLUME_TYPES = Collections.unmodifiableSet(
            EnumSet.of(S3_V1, S3_V1_READ_ONLY, S3_V2));

    public static final PrimitiveValueConverter<FsVolumeType> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(FsVolumeType.class, FsVolumeType.values());

    private final int id;
    private final String displayValue;
    private final boolean readOnly;

    FsVolumeType(final int id,
                 final String displayValue,
                 final boolean readOnly) {
        this.id = id;
        this.displayValue = displayValue;
        this.readOnly = readOnly;
    }

    public static FsVolumeType fromId(final int id) {
        final byte b = PrimitiveValueConverter.castId(FsVolumeType.class, id);
        return PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(b, STANDARD);
    }

    public int getId() {
        return id;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    @Override
    public byte getPrimitiveValue() {
        return (byte) id;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    /**
     * @return Volume types that use S3 for storage.
     */
    public static Set<FsVolumeType> getS3VolumeTypes() {
        return S3_VOLUME_TYPES;
    }

    /**
     * @return True if volumeType is an S3 volume type.
     */
    public static boolean isS3VolumeType(final FsVolumeType volumeType) {
        return volumeType != null && getS3VolumeTypes().contains(volumeType);
    }
}
