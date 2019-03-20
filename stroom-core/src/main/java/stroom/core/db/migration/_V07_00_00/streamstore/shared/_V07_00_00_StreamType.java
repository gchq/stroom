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

package stroom.core.db.migration._V07_00_00.streamstore.shared;

import stroom.core.db.migration._V07_00_00.entity.shared._V07_00_00_NamedEntity;
import stroom.core.db.migration._V07_00_00.entity.shared._V07_00_00_PrimitiveValueConverter;
import stroom.core.db.migration._V07_00_00.entity.shared._V07_00_00_SQLNameConstants;
import stroom.core.db.migration._V07_00_00.entity.shared._V07_00_00_HasPrimitiveValue;

/** Used for legacy migration **/
public class _V07_00_00_StreamType extends _V07_00_00_NamedEntity implements _V07_00_00_HasPrimitiveValue {
    public static final String TABLE_NAME = _V07_00_00_SQLNameConstants.STREAM + _V07_00_00_SQLNameConstants.TYPE_SUFFIX;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String PATH = _V07_00_00_SQLNameConstants.PATH;
    public static final String EXTENSION = _V07_00_00_SQLNameConstants.EXTENSION;
    public static final String ENTITY_TYPE = "StreamType";
    /**
     * Saved raw version for the archive.
     */
    public static final _V07_00_00_StreamType MANIFEST = new _V07_00_00_StreamType("MANIFEST", "mf", "Manifest", 1, Purpose.META);
    /**
     * Saved raw version for the archive.
     */
    public static final _V07_00_00_StreamType RAW_EVENTS = new _V07_00_00_StreamType("RAW_EVENTS", "revt", "Raw Events", 11, Purpose.RAW);
    /**
     * Saved raw version for the archive.
     */
    public static final _V07_00_00_StreamType RAW_REFERENCE = new _V07_00_00_StreamType("RAW_REFERENCE", "rref", "Raw Reference", 12,
            Purpose.RAW);
    /**
     * Processed events Data files.
     */
    public static final _V07_00_00_StreamType EVENTS = new _V07_00_00_StreamType("EVENTS", "evt", "Events", 21, Purpose.PROCESSED);
    /**
     * Processed reference Data files.
     */
    public static final _V07_00_00_StreamType REFERENCE = new _V07_00_00_StreamType("REFERENCE", "ref", "Reference", 22, Purpose.PROCESSED);
    /**
     * Processed Data files conforming to the Records XMLSchema.
     */
    public static final _V07_00_00_StreamType RECORDS = new _V07_00_00_StreamType("RECORDS", "rec", "Records", 71, Purpose.PROCESSED);
    /**
     * Test events Data files.
     */
    public static final _V07_00_00_StreamType TEST_EVENTS = new _V07_00_00_StreamType("TEST_EVENTS", "tevt", "Test Events", 51,
            Purpose.PROCESSED);
    /**
     * Test reference Data files.
     */
    public static final _V07_00_00_StreamType TEST_REFERENCE = new _V07_00_00_StreamType("TEST_REFERENCE", "tref", "Test Reference", 52,
            Purpose.PROCESSED);
    /**
     * Segment Index File used to mark a stream (e.g. by line).
     */
    public static final _V07_00_00_StreamType SEGMENT_INDEX = new _V07_00_00_StreamType("SEGMENT_INDEX", "seg", "Segment Index", 31,
            Purpose.INDEX);
    /**
     * Boundary Index File used to mark a stream (e.g. by file by file).
     */
    public static final _V07_00_00_StreamType BOUNDARY_INDEX = new _V07_00_00_StreamType("BOUNDARY_INDEX", "bdy", "Boundary Index", 32,
            Purpose.INDEX);
    /**
     * Meta stream data
     */
    public static final _V07_00_00_StreamType META = new _V07_00_00_StreamType("META", "meta", "Meta Data", 33, Purpose.META);
    /**
     * Processed events Data files.
     */
    public static final _V07_00_00_StreamType ERROR = new _V07_00_00_StreamType("ERROR", "err", "Error", 35, Purpose.ERROR);
    /**
     * Context file for use with an events file.
     */
    public static final _V07_00_00_StreamType CONTEXT = new _V07_00_00_StreamType("CONTEXT", "ctx", "Context", 34, Purpose.CONTEXT);
    private static final long serialVersionUID = 2059206179226911212L;
    private static final _V07_00_00_StreamType[] INITIAL_ALL_TYPES = new _V07_00_00_StreamType[]{MANIFEST, RAW_EVENTS, RAW_REFERENCE,
            EVENTS, REFERENCE, RECORDS, TEST_EVENTS, TEST_REFERENCE, SEGMENT_INDEX, BOUNDARY_INDEX, META, ERROR, CONTEXT};
    private String extension;
    private String path;
    private byte ppurpose;

    public _V07_00_00_StreamType(final String pathValue, final String extValue, final String name, final int primitiveValue,
                                 final Purpose purpose) {
        this.path = pathValue;
        this.extension = extValue;
        this.setName(name);
        this.setId(primitiveValue);
        this.setPurpose(purpose);
    }
    public _V07_00_00_StreamType() {
        // Default constructor necessary for GWT serialisation.
    }

    public static final _V07_00_00_StreamType[] initialValues() {
        return INITIAL_ALL_TYPES;
    }

    public static final _V07_00_00_StreamType createStub(final long pk) {
        final _V07_00_00_StreamType streamType = new _V07_00_00_StreamType();
        streamType.setStub(pk);
        return streamType;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(final String extension) {
        this.extension = extension;
    }

    public String getPath() {
        return path;
    }

    public void setPath(final String path) {
        this.path = path;
    }

    @Override
    public byte getPrimitiveValue() {
        return (byte) getId();
    }

    public byte getPpurpose() {
        return ppurpose;
    }

    public void setPpurpose(final byte ppurpose) {
        this.ppurpose = ppurpose;
    }

    public Purpose getPurpose() {
        return Purpose.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(ppurpose);
    }

    public void setPurpose(final Purpose purpose) {
        this.ppurpose = purpose.getPrimitiveValue();
    }

    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }

    public boolean isStreamTypeLazy() {
        if (getId() == SEGMENT_INDEX.getId()) {
            return true;
        }
        return getId() == BOUNDARY_INDEX.getId();

    }

    public boolean isStreamTypeSegment() {
        if (getId() == SEGMENT_INDEX.getId()) {
            return true;
        }
        return getId() == BOUNDARY_INDEX.getId();
    }

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

    public boolean isStreamTypeRaw() {
        return Purpose.RAW.equals(getPurpose());
    }

    public boolean isStreamTypeProcessed() {
        return Purpose.PROCESSED.equals(getPurpose());
    }

    public boolean isStreamTypeError() {
        return Purpose.ERROR.equals(getPurpose());
    }

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

    private enum Purpose implements _V07_00_00_HasPrimitiveValue {
        META(0, true), INDEX(1, true), CONTEXT(2, true), RAW(10, false), PROCESSED(11, false), ERROR(50, false);

        public static final _V07_00_00_PrimitiveValueConverter<Purpose> PRIMITIVE_VALUE_CONVERTER = new _V07_00_00_PrimitiveValueConverter<>(
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
