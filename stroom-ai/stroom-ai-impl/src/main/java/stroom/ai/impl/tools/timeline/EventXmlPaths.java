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

package stroom.ai.impl.tools.timeline;

import stroom.ai.impl.tools.generation.XmlElement;
import stroom.ai.impl.tools.generation.XmlParser;
import stroom.util.shared.NullSafe;

import java.util.List;
import java.util.Optional;

/// Reads one value of an event from its Events XML by its event-logging path below `Event` (e.g.
/// `EventSource/Device/HostName`, or `EventDetail/*/*/Subject` with `*` for any one element), as an index plan maps
/// it: never by matching values. For a timeline reading a thing the feeding pipelines' plans put in different
/// fields, where the event's own pipeline isn't known.
final class EventXmlPaths {

    private EventXmlPaths() {
        // Static only.
    }

    /// @param xml   One event's Events XML: an `Events` document holding it, or the `Event` element alone.
    /// @param paths The thing's paths, the most telling first.
    /// @return The first path's text the event holds (stripped), or empty if it holds none or doesn't parse.
    static Optional<String> value(final String xml, final List<String> paths) {
        if (NullSafe.isBlankString(xml) || NullSafe.isEmptyCollection(paths)) {
            return Optional.empty();
        }
        final XmlElement root;
        try {
            root = XmlParser.parse(xml, true, true);
        } catch (final RuntimeException e) {
            return Optional.empty();
        }
        final XmlElement event = "Event".equals(root.localName())
                ? root
                : root.iterElements().stream()
                        .filter(element -> "Event".equals(element.localName()))
                        .findFirst()
                        .orElse(null);
        if (event == null) {
            return Optional.empty();
        }
        for (final String path : paths) {
            final Optional<String> found = at(event, path.split("/"), 0);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    // The first text found by the path's parts from the element, depth first in document order.
    private static Optional<String> at(final XmlElement element, final String[] parts, final int from) {
        if (from == parts.length) {
            final String text = NullSafe.trim(element.text());
            return text.isEmpty()
                    ? Optional.empty()
                    : Optional.of(text);
        }
        for (final XmlElement child : element.childElements()) {
            if ("*".equals(parts[from]) || parts[from].equals(child.localName())) {
                final Optional<String> found = at(child, parts, from + 1);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }
}
