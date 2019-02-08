package stroom.data.store.impl.fs;

public class InternalStreamTypeNames {
    /**
     * Saved raw version for the archive.
     */
    public static final String MANIFEST = "Manifest";
    /**
     * Segment Index File used to mark a stream (e.g. by line).
     */
    public static final String SEGMENT_INDEX = "Segment Index";
    /**
     * Boundary Index File used to mark a stream (e.g. by file by file).
     */
    public static final String BOUNDARY_INDEX = "Boundary Index";
}
