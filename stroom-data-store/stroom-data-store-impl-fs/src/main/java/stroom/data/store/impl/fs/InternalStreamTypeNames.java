package stroom.data.store.impl.fs;

class InternalStreamTypeNames {
    /**
     * Saved raw version for the archive.
     */
    static final String MANIFEST = "Manifest";
    /**
     * Segment Index File used to mark a stream (e.g. by line).
     */
    static final String SEGMENT_INDEX = "Segment Index";
    /**
     * Boundary Index File used to mark a stream (e.g. by file by file).
     */
    static final String BOUNDARY_INDEX = "Boundary Index";
}
