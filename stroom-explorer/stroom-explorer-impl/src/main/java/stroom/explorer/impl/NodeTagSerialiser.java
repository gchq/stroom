/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.explorer.impl;

import stroom.explorer.shared.ExplorerNode;
import stroom.util.shared.NullSafe;

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
