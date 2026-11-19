package edu.ycp.cs.dh.acegwt.client.ace;

/**
 * Enumeration for ACE annotation types.
 */
public enum AceAnnotationType {

    // Each has Stroom's own gutter class (code-editor.css draws its icon), so a fatal error can look
    // different from an error, and the Ace type it is (which Ace names it by, e.g. for screen
    // readers). Ace (1.44) takes the class as the annotation's className, so ace.js
    // needs no change for it.

    INFO("stroom_info", "info"),
    WARNING("stroom_warning", "warning"),
    ERROR("stroom_error", "error"),
    FATAL_ERROR("stroom_fatal_error", "error");

    private final String name;
    private final String aceType;

    AceAnnotationType(final String name, final String aceType) {
        this.name = name;
        this.aceType = aceType;
    }

    /**
     * @return the theme name (e.g., "error")
     */
    public String getName() {
        return name;
    }

    /// @return The Ace annotation type it is: "info", "warning" or "error".
    public String getAceType() {
        return aceType;
    }
}
