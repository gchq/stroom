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

package stroom.streamstore.shared;

import stroom.entity.shared.HasPrimitiveValue;
import stroom.entity.shared.NamedEntity;
import stroom.entity.shared.PrimitiveValueConverter;
import stroom.entity.shared.SQLNameConstants;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

/**
 * List of all known stream types within the system.
 */
@Entity
@Table(name = "STRM_TP", uniqueConstraints = @UniqueConstraint(columnNames = {SQLNameConstants.NAME}))
public class StreamTypeEntity extends NamedEntity implements HasPrimitiveValue {
    public static final String TABLE_NAME = SQLNameConstants.STREAM + SQLNameConstants.TYPE_SUFFIX;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String ENTITY_TYPE = "StreamType";
    /**
     * Saved raw version for the archive.
     */
    public static final StreamTypeEntity MANIFEST = new StreamTypeEntity("Manifest", 1);
    /**
     * Saved raw version for the archive.
     */
    public static final StreamTypeEntity RAW_EVENTS = new StreamTypeEntity("Raw Events", 11);
    /**
     * Saved raw version for the archive.
     */
    public static final StreamTypeEntity RAW_REFERENCE = new StreamTypeEntity("Raw Reference", 12);
    /**
     * Processed events Data files.
     */
    public static final StreamTypeEntity EVENTS = new StreamTypeEntity("Events", 21);
    /**
     * Processed reference Data files.
     */
    public static final StreamTypeEntity REFERENCE = new StreamTypeEntity("Reference", 22);
    /**
     * Test events Data files.
     */
    public static final StreamTypeEntity TEST_EVENTS = new StreamTypeEntity("Test Events", 51);
    /**
     * Test reference Data files.
     */
    public static final StreamTypeEntity TEST_REFERENCE = new StreamTypeEntity("Test Reference", 52);
    /**
     * Segment Index File used to mark a stream (e.g. by line).
     */
    public static final StreamTypeEntity SEGMENT_INDEX = new StreamTypeEntity("Segment Index", 31);
    /**
     * Boundary Index File used to mark a stream (e.g. by file by file).
     */
    public static final StreamTypeEntity BOUNDARY_INDEX = new StreamTypeEntity("Boundary Index", 32);
    /**
     * Meta stream data
     */
    public static final StreamTypeEntity META = new StreamTypeEntity("Meta Data", 33);
    /**
     * Processed events Data files.
     */
    public static final StreamTypeEntity ERROR = new StreamTypeEntity("Error", 35);
    /**
     * Context file for use with an events file.
     */
    public static final StreamTypeEntity CONTEXT = new StreamTypeEntity("Context", 34);
    private static final long serialVersionUID = 2059206179226911212L;
    private static final StreamTypeEntity[] INITIAL_ALL_TYPES = new StreamTypeEntity[]{MANIFEST, RAW_EVENTS, RAW_REFERENCE,
            EVENTS, REFERENCE, TEST_EVENTS, TEST_REFERENCE, SEGMENT_INDEX, BOUNDARY_INDEX, META, ERROR, CONTEXT};

    public StreamTypeEntity(final String name, final int primitiveValue) {
        this.setName(name);
        this.setId(primitiveValue);
    }

    public StreamTypeEntity() {
        // Default constructor necessary for GWT serialisation.
    }

    public static final StreamTypeEntity[] initialValues() {
        return INITIAL_ALL_TYPES;
    }

    public static final StreamTypeEntity createStub(final long pk) {
        final StreamTypeEntity streamType = new StreamTypeEntity();
        streamType.setStub(pk);
        return streamType;
    }

    @Override
    @Transient
    public byte getPrimitiveValue() {
        return (byte) getId();
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }

    public enum Purpose implements HasPrimitiveValue {
        META(0, true), INDEX(1, true), CONTEXT(2, true), RAW(10, false), PROCESSED(11, false), ERROR(50, false);

        public static final PrimitiveValueConverter<Purpose> PRIMITIVE_VALUE_CONVERTER = new PrimitiveValueConverter<>(
                Purpose.values());
        private final byte primitiveValue;
        private final boolean nested;

        Purpose(final int primitiveValue, final boolean nested) {
            this.primitiveValue = (byte) primitiveValue;
            this.nested = nested;
        }

        @Override
        public byte getPrimitiveValue() {
            return primitiveValue;
        }

        public boolean isNested() {
            return nested;
        }
    }

    /**
     * Types of file we are dealing with.
     */
    public enum FileStoreType {
        dat, // The cached uncompressed file.
        bgz // Block GZIP Compressed File.
    }

}
