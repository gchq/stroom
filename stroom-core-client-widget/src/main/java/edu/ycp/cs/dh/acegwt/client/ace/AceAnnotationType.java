package edu.ycp.cs.dh.acegwt.client.ace;

/**
 * Enumeration for ACE annotation types.
 */
public enum AceAnnotationType {

    // IMPORTANT:
    // We have some custom stroom code in ace.js, so we can support
    // FATAL_ERROR in addition to the other ones. If Ace is updated and
    // that custom code is not put back then the gutter markers will break.

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
