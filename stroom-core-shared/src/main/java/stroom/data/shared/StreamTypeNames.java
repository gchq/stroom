package stroom.data.shared;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class StreamTypeNames {

    // TODO 06/01/2022 AT: The ultimate aim is for stream types to be fully configurable and not baked in
    //  however we have a lot of code that is conditional on certain stream type names so we have to make do
    //  for now.

    // TODO 06/01/2022 AT: This ought to be an enum but not going to take the risk of a refactor
    //  when we are about to deploy v7.0 and the resulting merge pain. A job for master.

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

    // GWT so can't use Set.of() :-(
    public static final Set<String> ALL_TYPE_NAMES = new HashSet<>(Arrays.asList(
            CONTEXT,
            DETECTIONS,
            ERROR,
            EVENTS,
            META,
            RAW_EVENTS,
            RAW_REFERENCE,
            RECORDS,
            REFERENCE,
            TEST_EVENTS,
            TEST_REFERENCE));
}
