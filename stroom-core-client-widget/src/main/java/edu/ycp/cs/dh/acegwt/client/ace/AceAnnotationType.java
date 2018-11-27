package edu.ycp.cs.dh.acegwt.client.ace;

/**
 * Enumeration for ACE annotation types.
 */
public enum AceAnnotationType {
    INFO("stroom_info"),
    WARNING("stroom_warning"),
    ERROR("stroom_error"),
    FATAL_ERROR("stroom_fatal_error");

    private final String name;

    AceAnnotationType(final String name) {
        this.name = name;
    }

    /**
     * @return the theme name (e.g., "error")
     */
    public String getName() {
        return name;
    }
}
