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

import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

public enum FsVolumeType implements HasDisplayValue, HasPrimitiveValue {

    // ************ IMPORTANT!!!!!! ************
    // Do NOT change the id values unless you fully understand the consequences.
    // They are stored in the DB, so changing them will impact the volume type
    // used for existing volumes, and likely break things.
    // ************ IMPORTANT!!!!!! ************

    /**
     * Uses a posix file system that is mounted locally.
     */
    STANDARD(0, "Standard"),

    /**
     * The first iteration of an S3 stream store implementation.
     * Writes all files (dat/meta/ctx/dat.idx/mf/etc) un-compressed to a temp dir, zips them up
     * then uploads the file to S3.
     * Reads are done by downloading the zip file from S3 to a temp dir and unzipping it.
     */
    S3_V1(1, "S3 v1"),

    /**
     * The second iteration of an S3 stream store implementation.
     * Writes all the data to a temp dir. Data is compressed with zstd. Each segment is compressed
     * as a separate zstd frame. The segment index is included at the end of the zstd file as a skippable frame.
     * If there is sufficient data to create a dictionary, then a dictionary will be used.
     */
    S3_V2(2, "S3 v2"),
    ;

    public static final PrimitiveValueConverter<FsVolumeType> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(FsVolumeType.class, FsVolumeType.values());

    private final int id;
    private final String displayValue;

    FsVolumeType(final int id,
                 final String displayValue) {
        this.id = id;
        this.displayValue = displayValue;
    }

    public static FsVolumeType fromId(final int id) {
        final byte b;
        try {
            b = (byte) id;
        } catch (final Exception e) {
            throw new IllegalArgumentException("Invalid id " + id);
        }
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
}
