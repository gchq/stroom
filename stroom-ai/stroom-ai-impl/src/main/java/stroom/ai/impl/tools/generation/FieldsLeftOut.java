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

import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/// The sample's fields a hand-written translation never reads, for the mapping read back from it to leave out on
/// purpose (`ignored_fields`; decided by the user, 2026-11-25). The XSLT regenerated from that mapping then logs what
/// the original did, not a "No rule reads field" line for each field the original left out (seen in eval case 105:
/// the regenerated XSLT logged src_ip, which the original never read, and the agent mapped it unasked); mapping one
/// of them is the user's explicit request (a field both mapped and ignored is read, and never logged).
///
/// A field counts as read where the original names it as a quoted literal anywhere (`@key='src_ip'`,
/// `@name="src_ip"`): a field it never names it can't read by name. An XSLT that logs its own unread fields (one the
/// generator wrote) leaves nothing out: it already logs what the regenerated one would. Only Data Splitter and JSON
/// input, whose unread fields the generator logs.
public final class FieldsLeftOut {

    private static final List<String> LOGGED_INPUTS = List.of("data_splitter", "json");

    private FieldsLeftOut() {
        // Static only.
    }

    /// @param code         The original XSLT's code, or null.
    /// @param mapping      The mapping read back from it, or null.
    /// @param sampleFields The sample records' field names, as the translation reads them, or null.
    /// @return The sample's fields the original never names, the mapping doesn't read and doesn't already ignore,
    /// in the sample's order, each once; empty where there are none or the input's unread fields aren't logged.
    public static List<String> of(final String code,
                                  final TranslationMapping mapping,
                                  final Collection<String> sampleFields) {
        if (NullSafe.isBlankString(code) || mapping == null || NullSafe.isEmptyCollection(sampleFields)
            || !LOGGED_INPUTS.contains(mapping.input()) || code.contains(XsltGenerator.UNREAD_FIELD)) {
            return List.of();
        }
        final Set<String> out = new LinkedHashSet<>();
        for (final String field : sampleFields) {
            if (NullSafe.isBlankString(field) || mapping.ignoredFields().contains(field)
                || MappingFacts.readsInput(mapping, field) || named(code, field)) {
                continue;
            }
            out.add(field);
        }
        return List.copyOf(out);
    }

    /// @param mapping The mapping read back.
    /// @param leftOut The fields left out ([#of]).
    /// @return The mapping ignoring them as well as those it ignored already; the mapping itself where none are
    /// new.
    public static TranslationMapping ignoring(final TranslationMapping mapping, final List<String> leftOut) {
        final Set<String> ignored = new LinkedHashSet<>(mapping.ignoredFields());
        if (!ignored.addAll(NullSafe.list(leftOut))) {
            return mapping;
        }
        return mapping.withIgnoredFields(new ArrayList<>(ignored));
    }

    /// @param leftOut The fields left out ([#of]), not empty.
    /// @return Them in words, for the save form and the result: `left out, as the original: src_ip`.
    public static String said(final List<String> leftOut) {
        return "left out, as the original: " + String.join(", ", leftOut);
    }

    // Whether the code names the field as a quoted literal, the whole literal.
    private static boolean named(final String code, final String field) {
        final String quoted = Pattern.quote(field);
        return Pattern.compile("'" + quoted + "'|\"" + quoted + "\"|&quot;" + quoted + "&quot;|&apos;" + quoted
                               + "&apos;").matcher(code).find();
    }
}
