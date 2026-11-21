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

package stroom.ai.impl.tools.draft;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// The paths into held XML (xml_fields) the draft maps: one a node. [LocalCheck#withXmlFields] gives each element
/// of a field's XML as `<field>/<path from the root>` and as `<field>//<name>`, and each attribute under both and as
/// `<field>//@<name>` too, so the agent may read a node either way; mapped as they come, each value was written two
/// or three times as Data (seen, chat 182: a Teams message body's `//p` and `/div/p`, `//@alt`, `//emoji/@alt` and
/// `/div/p/emoji/@alt`). An element is drafted by its full path when its name is found at one depth, by `//<name>`
/// only when found at several (where one full path would miss the others), never both, and an attribute only under
/// its element's chosen path.
final class HeldXmlPaths {

    private static final String ANYWHERE = "//";
    private static final String ATTRIBUTE = "/@";

    private HeldXmlPaths() {
        // Static only.
    }

    /// @param names     The records' field names, those [LocalCheck#withXmlFields] added among them.
    /// @param xmlFields The fields holding XML.
    /// @return The names the draft leaves out: each node's other paths, beside the one it maps.
    static Set<String> leftOut(final List<String> names, final List<String> xmlFields) {
        final Set<String> out = new LinkedHashSet<>();
        for (final String field : xmlFields) {
            leftOut(names, field, out);
        }
        return out;
    }

    private static void leftOut(final List<String> names, final String field, final Set<String> out) {
        final String prefix = field + "/";
        // Each element name -> the depths of the full paths it is found at.
        final Map<String, Set<Integer>> depths = new HashMap<>();
        for (final String name : names) {
            if (name.startsWith(prefix) && !name.startsWith(field + ANYWHERE)) {
                final String path = elementPath(name.substring(prefix.length()));
                final String[] steps = path.split("/", -1);
                depths.computeIfAbsent(steps[steps.length - 1], ignored -> new HashSet<>()).add(steps.length);
            }
        }
        for (final String name : names) {
            if (!name.startsWith(prefix)) {
                continue;
            }
            if (name.startsWith(field + ANYWHERE + "@")) {
                // An attribute anywhere: read under its element.
                out.add(name);
            } else if (name.startsWith(field + ANYWHERE)) {
                final String element = elementPath(name.substring(field.length() + ANYWHERE.length()));
                if (depths.getOrDefault(element, Set.of()).size() <= 1) {
                    out.add(name);
                }
            } else {
                final String[] steps = elementPath(name.substring(prefix.length())).split("/", -1);
                if (depths.getOrDefault(steps[steps.length - 1], Set.of()).size() > 1) {
                    out.add(name);
                }
            }
        }
    }

    // A path to a node, its attribute (if any) taken off: the element's.
    private static String elementPath(final String path) {
        final int at = path.lastIndexOf(ATTRIBUTE);
        return at < 0
                ? path
                : path.substring(0, at);
    }
}
