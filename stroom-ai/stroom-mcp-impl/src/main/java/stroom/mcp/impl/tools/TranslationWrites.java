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

package stroom.mcp.impl.tools;

import stroom.util.shared.NullSafe;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Whether the translation that wrote some Events writes an event path, read from its XSLT rather than from the
/// Events. Seen in testing: a byte count the Events held early in their stream was "not in the sample" of events read
/// spread over it, and the index plan's hint said to leave it out. A sample can say how often a path is populated,
/// never that it isn't: what the translation can write says that.
///
/// Read from the XSLT's text: an element of the path's last name written (`<InboundSize>`, or `xsl:element` named
/// so), or a Data entry's Name as a literal (`<Data Name="bytes"/>`, or `mcp:data('bytes', ...)` as the tools write
/// it). A translation that reads a field of that name to write it is taken as writing it.
final class TranslationWrites {

    // A Data entry's Name in a source: EventDetail//Data[@Name='bytes']/@Value.
    private static final Pattern DATA_NAME = Pattern.compile("\\[@Name\\s*=\\s*['\"]([^'\"]+)['\"]\\]");
    // A step's predicates, dropped to leave its name.
    private static final Pattern PREDICATE = Pattern.compile("\\[[^\\]]*\\]");

    private TranslationWrites() {
        // Static only.
    }

    /// @param codes The code of the translation XSLTs that wrote some Events.
    /// @return Whether any of them writes a source path; false for every path when there are none.
    static Predicate<String> of(final List<String> codes) {
        final List<String> held = List.copyOf(NullSafe.list(codes));
        return source -> held.stream().anyMatch(code -> writes(code, source));
    }

    /// @param code   An XSLT's code.
    /// @param source An event path, as an index field's source (`EventDetail/*/Payload/Resource/InboundSize`,
    ///               `EventDetail//Data[@Name='bytes']/@Value`).
    /// @return Whether the XSLT writes it.
    static boolean writes(final String code, final String source) {
        if (NullSafe.isBlankString(code) || NullSafe.isBlankString(source)) {
            return false;
        }
        final Matcher data = DATA_NAME.matcher(source);
        if (data.find()) {
            final String name = Pattern.quote(data.group(1));
            return Pattern.compile("['\"]" + name + "['\"]").matcher(code).find();
        }
        final String element = lastElement(source);
        if (element == null) {
            return false;
        }
        final String name = Pattern.quote(element);
        return Pattern.compile("<(?:[\\w.-]+:)?" + name + "[\\s/>]").matcher(code).find()
               || Pattern.compile("<xsl:element\\s+name=['\"](?:[\\w.-]+:)?" + name + "['\"]").matcher(code).find();
    }

    // The last element a path names: InboundSize in .../Resource/InboundSize, Data in .../Data/@Value; null for none.
    private static String lastElement(final String source) {
        final String[] steps = PREDICATE.matcher(source).replaceAll("").split("/");
        for (int i = steps.length - 1; i >= 0; i--) {
            final String step = steps[i].strip();
            if (!step.isEmpty() && !step.startsWith("@") && !"*".equals(step) && !"text()".equals(step)) {
                return step.contains(":")
                        ? step.substring(step.indexOf(':') + 1)
                        : step;
            }
        }
        return null;
    }
}
