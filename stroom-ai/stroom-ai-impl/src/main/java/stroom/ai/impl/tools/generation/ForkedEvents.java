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

package stroom.ai.impl.tools.generation;

import stroom.ai.impl.tools.validation.XmlTrees;
import stroom.ai.impl.tools.validation.XsltVersionHistory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// XSLTs that write Events in an environment's fork of the event-logging schema (a namespace of its own, with the same
/// elements; seen: `file://xml/schema/accounting/events`), read as if they wrote `event-logging:3`. What a
/// translation maps doesn't depend on the namespace it writes in, so the readers ([XsltReader], [MappingRebuild])
/// stay as they are and read a fork's XSLT once its namespace is renamed.
final class ForkedEvents {

    private static final String XSL = XmlTrees.XSL;

    private ForkedEvents() {
        // Static only.
    }

    /// @param code An XSLT's text.
    /// @return The namespace it writes `<Events>` or `<Event>` in, if that is not `event-logging:3`; else null
    /// (also for one that writes neither, or isn't well-formed).
    static String forkedNamespace(final String code) {
        final XmlElement root;
        try {
            root = XmlParser.parse(XsltVersionHistory.strip(code), true, false);
        } catch (final RuntimeException e) {
            return null;
        }
        for (final XmlElement element : root.iterElements()) {
            final String namespace = element.namespace();
            if (("Events".equals(element.localName()) || "Event".equals(element.localName()))
                && namespace != null && !namespace.isEmpty() && !XSL.equals(namespace)) {
                return XsltGenerator.EVT.equals(namespace)
                        ? null
                        : namespace;
            }
        }
        return null;
    }

    /// @param code An XSLT's text.
    /// @return It with the forked namespace it writes Events in renamed `event-logging:3`, wherever the namespace
    /// stands alone in an attribute (a namespace declaration, xpath-default-namespace, or the first half of an
    /// xsi:schemaLocation pair); a system id beginning with the same text is left as it is. Unchanged if it writes
    /// Events in `event-logging:3`.
    static String asStandard(final String code) {
        final String namespace = code == null
                ? null
                : forkedNamespace(code);
        if (namespace == null) {
            return code;
        }
        final Pattern alone = Pattern.compile("(?<=[\"'\\s])" + Pattern.quote(namespace) + "(?=[\"'\\s])");
        return alone.matcher(code).replaceAll(Matcher.quoteReplacement(XsltGenerator.EVT));
    }
}
