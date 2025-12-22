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
