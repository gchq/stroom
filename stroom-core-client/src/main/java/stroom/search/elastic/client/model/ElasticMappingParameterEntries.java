/*
 * Copyright 2026 Crown Copyright
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

package stroom.search.elastic.client.model;

import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/// Turns a field's mapping parameters into the Info tab's mapping entries.
///
/// The server lists nested parameter values by path, e.g. `index_options.type` or `copy_to[0]`. Each parameter
/// becomes one entry: a plain value is shown as it is, and a nested one is shown as a list with one option per
/// line, like the list of source indices. Each line is the option's path below the parameter and its value, e.g.
/// `type: int8_hnsw`, or just the value for an item of a list, e.g. `all`.
public final class ElasticMappingParameterEntries {

    // Orders the paths below a parameter by segment, with list indexes compared as numbers so [10] follows [9]
    private static final Comparator<String> PATH_ORDER = ElasticMappingParameterEntries::comparePaths;

    private ElasticMappingParameterEntries() {
    }

    /// @param mappingParameters the parameter values keyed by name or path, or `null`
    /// @return one entry per parameter, ordered by parameter name
    public static List<ElasticMappingEntry> create(final Map<String, String> mappingParameters) {
        // Parameter name -> path below the parameter -> value
        final Map<String, Map<String, String>> parameters = new TreeMap<>();
        NullSafe.forEach(mappingParameters, (key, value) -> {
            final int end = getNameEnd(key);
            parameters.computeIfAbsent(key.substring(0, end), name -> new TreeMap<>(PATH_ORDER))
                    .put(key.substring(end), value);
        });

        final List<ElasticMappingEntry> entries = new ArrayList<>();
        parameters.forEach((name, options) -> {
            if (options.size() == 1 && options.containsKey("")) {
                entries.add(new ElasticMappingEntry(name, options.get("")));
            } else {
                final List<String> lines = new ArrayList<>();
                options.forEach((path, value) -> lines.add(toLine(path, value)));
                entries.add(new ElasticMappingEntry(name, lines));
            }
        });
        return entries;
    }

    // The parameter name is the key up to its first '.' or '['
    private static int getNameEnd(final String key) {
        for (int i = 0; i < key.length(); i++) {
            final char c = key.charAt(i);
            if (c == '.' || c == '[') {
                return i;
            }
        }
        return key.length();
    }

    private static String toLine(final String path, final String value) {
        final String text = NullSafe.string(value);
        if (isOnlyIndexes(path)) {
            return text;
        }
        final String option = path.startsWith(".")
                ? path.substring(1)
                : path;
        return option + ": " + text;
    }

    // True for "", "[0]" or "[1][0]": a list item with no name of its own
    private static boolean isOnlyIndexes(final String path) {
        for (final String segment : splitPath(path)) {
            if (!segment.startsWith("[")) {
                return false;
            }
        }
        return true;
    }

    private static int comparePaths(final String path1, final String path2) {
        final List<String> segments1 = splitPath(path1);
        final List<String> segments2 = splitPath(path2);
        for (int i = 0; i < Math.min(segments1.size(), segments2.size()); i++) {
            final int result = compareSegments(segments1.get(i), segments2.get(i));
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(segments1.size(), segments2.size());
    }

    private static int compareSegments(final String segment1, final String segment2) {
        final Integer index1 = toIndex(segment1);
        final Integer index2 = toIndex(segment2);
        if (index1 != null && index2 != null) {
            return Integer.compare(index1, index2);
        }
        return segment1.compareTo(segment2);
    }

    private static Integer toIndex(final String segment) {
        if (segment.startsWith("[") && segment.endsWith("]")) {
            try {
                return Integer.parseInt(segment.substring(1, segment.length() - 1));
            } catch (final NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    // Splits ".a[0].b" into "a", "[0]" and "b"
    private static List<String> splitPath(final String path) {
        final List<String> segments = new ArrayList<>();
        final StringBuilder segment = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            final char c = path.charAt(i);
            if (c == '.' || c == '[') {
                addSegment(segments, segment);
                if (c == '[') {
                    segment.append(c);
                }
            } else if (c == ']') {
                segment.append(c);
                addSegment(segments, segment);
            } else {
                segment.append(c);
            }
        }
        addSegment(segments, segment);
        return segments;
    }

    private static void addSegment(final List<String> segments, final StringBuilder segment) {
        if (segment.length() > 0) {
            segments.add(segment.toString());
            segment.setLength(0);
        }
    }
}
