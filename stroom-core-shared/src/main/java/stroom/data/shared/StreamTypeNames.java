package stroom.data.shared;

public class StreamTypeNames {
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
     * Processed Data files conforming to the Records XMLSchema.
     */
    public static final String RECORDS = "Records";
    /**
     * Meta meta data
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

    /**
     * Processed test events data files
     */
    public static final String TEST_EVENTS = "Test Events";

    /**
     * Processed test reference data files
     */
    public static final String TEST_REFERENCE = "Test Reference";

    /**
     * Processed detections
     */
    public static final String DETECTIONS = "Detections";


    private static final String UI_NAME_HEADERS = "Headers";

    /**
     * The name of child types in the UI is different Meta => Headers
     */
    public static String asUiName(final String typeName) {
        if (META.equals(typeName)) {
            return UI_NAME_HEADERS;
        } else {
            return typeName;
        }
    }

}
