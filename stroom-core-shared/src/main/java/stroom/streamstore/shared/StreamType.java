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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

/**
 * List of all known stream types within the system.
 */
@Entity
@Table(name = "STRM_TP", uniqueConstraints = @UniqueConstraint(columnNames = {SQLNameConstants.NAME}))
public class StreamType extends NamedEntity implements HasPrimitiveValue {
    public static final String TABLE_NAME = SQLNameConstants.STREAM + SQLNameConstants.TYPE_SUFFIX;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String PATH = SQLNameConstants.PATH;
    public static final String EXTENSION = SQLNameConstants.EXTENSION;
    public static final String ENTITY_TYPE = "StreamType";
    /**
     * Saved raw version for the archive.
     */
    public static final StreamType MANIFEST = new StreamType("MANIFEST", "mf", "Manifest", 1, Purpose.META);
    /**
     * Saved raw version for the archive.
     */
    public static final StreamType RAW_EVENTS = new StreamType("RAW_EVENTS", "revt", "Raw Events", 11, Purpose.RAW);
    /**
     * Saved raw version for the archive.
     */
    public static final StreamType RAW_REFERENCE = new StreamType("RAW_REFERENCE", "rref", "Raw Reference", 12,
            Purpose.RAW);
    /**
     * Processed events Data files.
     */
    public static final StreamType EVENTS = new StreamType("EVENTS", "evt", "Events", 21, Purpose.PROCESSED);
    /**
     * Processed reference Data files.
     */
    public static final StreamType REFERENCE = new StreamType("REFERENCE", "ref", "Reference", 22, Purpose.PROCESSED);
    /**
     * Test events Data files.
     */
    public static final StreamType TEST_EVENTS = new StreamType("TEST_EVENTS", "tevt", "Test Events", 51,
            Purpose.PROCESSED);
    /**
     * Test reference Data files.
     */
    public static final StreamType TEST_REFERENCE = new StreamType("TEST_REFERENCE", "tref", "Test Reference", 52,
            Purpose.PROCESSED);
    /**
     * Segment Index File used to mark a stream (e.g. by line).
     */
    public static final StreamType SEGMENT_INDEX = new StreamType("SEGMENT_INDEX", "seg", "Segment Index", 31,
            Purpose.INDEX);
    /**
     * Boundary Index File used to mark a stream (e.g. by file by file).
     */
    public static final StreamType BOUNDARY_INDEX = new StreamType("BOUNDARY_INDEX", "bdy", "Boundary Index", 32,
            Purpose.INDEX);
    /**
     * Meta stream data
     */
    public static final StreamType META = new StreamType("META", "meta", "Meta Data", 33, Purpose.META);
    /**
     * Processed events Data files.
     */
    public static final StreamType ERROR = new StreamType("ERROR", "err", "Error", 35, Purpose.ERROR);
    /**
     * Context file for use with an events file.
     */
    public static final StreamType CONTEXT = new StreamType("CONTEXT", "ctx", "Context", 34, Purpose.CONTEXT);
    private static final long serialVersionUID = 2059206179226911212L;
    private static final StreamType[] INITIAL_ALL_TYPES = new StreamType[]{MANIFEST, RAW_EVENTS, RAW_REFERENCE,
            EVENTS, REFERENCE, TEST_EVENTS, TEST_REFERENCE, SEGMENT_INDEX, BOUNDARY_INDEX, META, ERROR, CONTEXT};
    private String extension;
    private String path;
    private byte ppurpose;

    public StreamType(final String pathValue, final String extValue, final String name, final int primitiveValue,
                      final Purpose purpose) {
        this.path = pathValue;
        this.extension = extValue;
        this.setName(name);
        this.setId(primitiveValue);
        this.setPurpose(purpose);
    }
    public StreamType() {
        // Default constructor necessary for GWT serialisation.
    }

    public static final StreamType[] initialValues() {
        return INITIAL_ALL_TYPES;
    }

    public static final StreamType createStub(final long pk) {
        final StreamType streamType = new StreamType();
        streamType.setStub(pk);
        return streamType;
    }

    @Column(name = EXTENSION, nullable = false)
    public String getExtension() {
        return extension;
    }

    public void setExtension(final String extension) {
        this.extension = extension;
    }

    @Column(name = PATH, nullable = false)
    public String getPath() {
        return path;
    }

    public void setPath(final String path) {
        this.path = path;
    }

    @Override
    @Transient
    public byte getPrimitiveValue() {
        return (byte) getId();
    }

    @Column(name = SQLNameConstants.PURPOSE, nullable = false)
    public byte getPpurpose() {
        return ppurpose;
    }

    public void setPpurpose(final byte ppurpose) {
        this.ppurpose = ppurpose;
    }

    @Transient
    public Purpose getPurpose() {
        return Purpose.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(ppurpose);
    }

    public void setPurpose(final Purpose purpose) {
        this.ppurpose = purpose.getPrimitiveValue();
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }

    @Transient
    public boolean isStreamTypeLazy() {
        if (getId() == SEGMENT_INDEX.getId()) {
            return true;
        }
        return getId() == BOUNDARY_INDEX.getId();

    }

    @Transient
    public boolean isStreamTypeSegment() {
        if (getId() == SEGMENT_INDEX.getId()) {
            return true;
        }
        return getId() == BOUNDARY_INDEX.getId();
    }

    @Transient
    public boolean isStreamTypeChild() {
        if (getId() == SEGMENT_INDEX.getId()) {
            return true;
        }
        if (getId() == BOUNDARY_INDEX.getId()) {
            return true;
        }
        if (getId() == MANIFEST.getId()) {
            return true;
        }
        if (getId() == META.getId()) {
            return true;
        }
        return getId() == CONTEXT.getId();
    }

    @Transient
    public boolean isStreamTypeRaw() {
        return Purpose.RAW.equals(getPurpose());
    }

    @Transient
    public boolean isStreamTypeProcessed() {
        return Purpose.PROCESSED.equals(getPurpose());
    }

    @Transient
    public boolean isStreamTypeError() {
        return Purpose.ERROR.equals(getPurpose());
    }

    @Transient
    public FileStoreType getFileStoreType() {
        if (getId() == SEGMENT_INDEX.getId()) {
            return FileStoreType.dat;
        }
        if (getId() == BOUNDARY_INDEX.getId()) {
            return FileStoreType.dat;
        }
        if (getId() == MANIFEST.getId()) {
            return FileStoreType.dat;
        }
        return FileStoreType.bgz;
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
