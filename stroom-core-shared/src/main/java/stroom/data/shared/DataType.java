package stroom.data.shared;

public enum DataType {
    /**
     * Data that has been demarcated into segments, e.g. cooked events
     */
    SEGMENTED,
    /**
     * Data that has not been demarcated into segments, i.e. raw un-cooked data.
     */
    NON_SEGMENTED,

    MARKER
}
