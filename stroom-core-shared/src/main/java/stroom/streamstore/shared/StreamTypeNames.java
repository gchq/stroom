package stroom.streamstore.shared;

public class StreamTypeNames {
    /**
     * Saved raw version for the archive.
     */
    public static final String MANIFEST = "Manifest";
    /**
     * Saved raw version for the archive.
     */
    public static final String RAW_EVENTS = "Raw Events";
    /**
     * Saved raw version for the archive.
     */
    public static final String RAW_REFERENCE = "Raw Reference";
    /**
     * Processed events Data files.
     */
    public static final String EVENTS = "Events";
    /**
     * Processed reference Data files.
     */
    public static final String REFERENCE = "Reference";
    /**
     * Test events Data files.
     */
    public static final String TEST_EVENTS = "Test Events";
    /**
     * Test reference Data files.
     */
    public static final String TEST_REFERENCE = "Test Reference";
    /**
     * Segment Index File used to mark a stream (e.g. by line).
     */
    public static final String SEGMENT_INDEX = "Segment Index";
    /**
     * Boundary Index File used to mark a stream (e.g. by file by file).
     */
    public static final String BOUNDARY_INDEX = "Boundary Index";
    /**
     * Meta stream data
     */
    public static final String META = "Meta Data";
    /**
     * Processed events Data files.
     */
    public static final String ERROR = "Error";
    /**
     * Context file for use with an events file.
     */
    public static final String CONTEXT = "Context";
}
