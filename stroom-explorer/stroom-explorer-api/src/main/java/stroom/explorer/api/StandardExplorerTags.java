package stroom.explorer.api;

/**
 * Explorer tags that may be used internally by stroom to apply conditional logic to nodes.
 * The distinction between these and {@link stroom.explorer.shared.NodeFlag}s is that tags
 * have to be set on nodes by a user rather than in code.
 * All these tags will be included in the list of all known tags so can be applied to nodes
 * by users.
 */
public enum StandardExplorerTags {

    EXTRACTION_PIPELINE("extraction-pipeline");

    private final String tagName;

    StandardExplorerTags(final String tagName) {
        this.tagName = tagName;
    }

    public String getTagName() {
        return tagName;
    }
}
