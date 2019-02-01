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

package stroom.pipeline.feed;

import stroom.entity.shared.HasPrimitiveValue;
import stroom.entity.shared.PrimitiveValueConverter;
import stroom.entity.shared.SQLNameConstants;
import stroom.importexport.migration.NamedEntity;

/**
 * Used for legacy migration
 **/
@Deprecated
public class OldStreamType extends NamedEntity {
    public static final String PATH = SQLNameConstants.PATH;
    public static final String EXTENSION = SQLNameConstants.EXTENSION;
    public static final String ENTITY_TYPE = "StreamType";
    /**
     * Saved raw version for the archive.
     */
    public static final OldStreamType MANIFEST = new OldStreamType("MANIFEST", "mf", "Manifest", 1, Purpose.META);
    /**
     * Saved raw version for the archive.
     */
    public static final OldStreamType RAW_EVENTS = new OldStreamType("RAW_EVENTS", "revt", "Raw Events", 11, Purpose.RAW);
    /**
     * Saved raw version for the archive.
     */
    public static final OldStreamType RAW_REFERENCE = new OldStreamType("RAW_REFERENCE", "rref", "Raw Reference", 12,
            Purpose.RAW);
    /**
     * Processed events Data files.
     */
    public static final OldStreamType EVENTS = new OldStreamType("EVENTS", "evt", "Events", 21, Purpose.PROCESSED);
    /**
     * Processed reference Data files.
     */
    public static final OldStreamType REFERENCE = new OldStreamType("REFERENCE", "ref", "Reference", 22, Purpose.PROCESSED);
    /**
     * Processed Data files conforming to the Records XMLSchema.
     */
    public static final OldStreamType RECORDS = new OldStreamType("RECORDS", "rec", "Records", 71, Purpose.PROCESSED);
    /**
     * Test events Data files.
     */
    public static final OldStreamType TEST_EVENTS = new OldStreamType("TEST_EVENTS", "tevt", "Test Events", 51,
            Purpose.PROCESSED);
    /**
     * Test reference Data files.
     */
    public static final OldStreamType TEST_REFERENCE = new OldStreamType("TEST_REFERENCE", "tref", "Test Reference", 52,
            Purpose.PROCESSED);
    /**
     * Segment Index File used to mark a stream (e.g. by line).
     */
    public static final OldStreamType SEGMENT_INDEX = new OldStreamType("SEGMENT_INDEX", "seg", "Segment Index", 31,
            Purpose.INDEX);
    /**
     * Boundary Index File used to mark a stream (e.g. by file by file).
     */
    public static final OldStreamType BOUNDARY_INDEX = new OldStreamType("BOUNDARY_INDEX", "bdy", "Boundary Index", 32,
            Purpose.INDEX);
    /**
     * Meta meta data
     */
    public static final OldStreamType META = new OldStreamType("META", "meta", "Meta Data", 33, Purpose.META);
    /**
     * Processed events Data files.
     */
    public static final OldStreamType ERROR = new OldStreamType("ERROR", "err", "Error", 35, Purpose.ERROR);
    /**
     * Context file for use with an events file.
     */
    public static final OldStreamType CONTEXT = new OldStreamType("CONTEXT", "ctx", "Context", 34, Purpose.CONTEXT);
    private static final long serialVersionUID = 2059206179226911212L;
    private static final OldStreamType[] INITIAL_ALL_TYPES = new OldStreamType[]{MANIFEST, RAW_EVENTS, RAW_REFERENCE,
            EVENTS, REFERENCE, RECORDS, TEST_EVENTS, TEST_REFERENCE, SEGMENT_INDEX, BOUNDARY_INDEX, META, ERROR, CONTEXT};
    private String extension;
    private String path;
    private byte ppurpose;

    public OldStreamType(final String pathValue, final String extValue, final String name, final int primitiveValue,
                         final Purpose purpose) {
        this.path = pathValue;
        this.extension = extValue;
        this.setName(name);
    }

    public OldStreamType() {
        // Default constructor necessary for GWT serialisation.
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


    public byte getPpurpose() {
        return ppurpose;
    }

    public void setPpurpose(final byte ppurpose) {
        this.ppurpose = ppurpose;
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
