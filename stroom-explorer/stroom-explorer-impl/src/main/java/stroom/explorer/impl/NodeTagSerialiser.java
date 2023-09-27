package stroom.explorer.impl;

import stroom.explorer.shared.ExplorerNode;
import stroom.util.NullSafe;

import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class NodeTagSerialiser {

    private static final Pattern TAG_DELIMITER_PATTERN = Pattern.compile(
            Pattern.quote(ExplorerNode.TAGS_DELIMITER) + "+");

    private NodeTagSerialiser() {
    }

    /**
     * @return tagsStr de-serialised to set of string. {@link ExplorerNode#TAGS_DELIMITER}
     * is used to split the string. If tagsStr is null or blank an empty set is returned.
     * All tags are converted to lowercase for consistency
     */
    public static Set<String> deserialise(final String tagsStr) {
        if (NullSafe.isBlankString(tagsStr)) {
            return Collections.emptySet();
        } else {
            return NullSafe.stream(TAG_DELIMITER_PATTERN.split(tagsStr.trim()))
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
        }
    }

    /**
     * @return tags serialised to a string delimited by {@link ExplorerNode#TAGS_DELIMITER},
     * or null if tags is null or empty. All tags are converted to lowercase for consistency.
     * The tags are serialised in natural order.
     */
    public static String serialise(final Set<String> tags) {
        if (NullSafe.hasItems(tags)) {
            // Sort so we have a consistent order in the db
            return tags.stream()
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .sorted()
                    .collect(Collectors.joining(ExplorerNode.TAGS_DELIMITER));
        } else {
            return null;
        }
    }
}
