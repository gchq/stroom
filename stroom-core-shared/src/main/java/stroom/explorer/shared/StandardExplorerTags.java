package stroom.explorer.shared;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Explorer tags that may be used internally by stroom to apply conditional logic to nodes.
 * The distinction between these and {@link stroom.explorer.shared.NodeFlag}s is that tags
 * have to be set on nodes by a user rather than in code.
 * All these tags will be included in the list of all known tags so can be applied to nodes
 * by users.
 */
public enum StandardExplorerTags {

    /**
     * For marking extraction pipelines
     */
    EXTRACTION("extraction"),
    /**
     * For marking dynamic extraction pipelines and dynamic indexes
     */
    DYNAMIC("dynamic"),
    /**
     * For marking reference loader pipelines
     */
    REFERENCE_LOADER("reference-loader"),
    ;

    private final String tagName;

    StandardExplorerTags(final String tagName) {
        this.tagName = tagName;
    }

    public String getTagName() {
        return tagName;
    }

    /**
     * Map the passed tags to their tag names and return a sorted set for consistency
     */
    public static Set<String> asTagNameSet(final StandardExplorerTags... tags) {
        if (tags == null || tags.length == 0) {
            return Collections.emptySet();
        } else if (tags.length == 1) {
            return Collections.singleton(tags[0].getTagName());
        } else {
            return Arrays.stream(tags)
                    .map(StandardExplorerTags::getTagName)
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }
}
