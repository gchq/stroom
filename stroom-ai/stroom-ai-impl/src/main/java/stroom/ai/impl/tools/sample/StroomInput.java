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

package stroom.ai.impl.tools.sample;

import stroom.pipeline.reader.Xml10Chars;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/// Text as Stroom's parsers read it: the characters XML 1.0 doesn't allow (control characters other than tab, new
/// line and carriage return) taken out first (`CombinedParser`, `InvalidXmlCharFilter`), whatever the source. Seen:
/// auditd's ENRICHED format separates its translated fields with a group separator (`\x1d`), so in Stroom a value
/// ran into the next key (`nametype=NORMALOUID="root"`).
public final class StroomInput {

    private static final Xml10Chars XML_10 = new Xml10Chars();
    private static final int MOST_NAMED = 5;

    private StroomInput() {
        // Static only.
    }

    /// @param text Text.
    /// @return The text without the characters Stroom's parser takes out.
    public static String asParsed(final String text) {
        if (text == null || text.codePoints().allMatch(XML_10::isValidLiteral)) {
            return text;
        }
        final StringBuilder out = new StringBuilder(text.length());
        text.codePoints().filter(XML_10::isValidLiteral).forEach(out::appendCodePoint);
        return out.toString();
    }

    /// @param texts Sample texts.
    /// @return What Stroom's parser would take out of them, and what to do where those characters separate fields;
    /// empty when nothing.
    public static Optional<String> removedNote(final List<String> texts) {
        final Map<Integer, Integer> removed = new TreeMap<>();
        texts.forEach(text -> text.codePoints()
                .filter(c -> !XML_10.isValidLiteral(c))
                .forEach(c -> removed.merge(c, 1, Integer::sum)));
        if (removed.isEmpty()) {
            return Optional.empty();
        }
        final StringBuilder named = new StringBuilder();
        removed.entrySet().stream().limit(MOST_NAMED).forEach(entry -> named.append(named.isEmpty()
                ? ""
                : ", ").append(String.format("\\x%02x", entry.getKey())).append(" x").append(entry.getValue()));
        return Optional.of("The sample holds characters XML doesn't allow (" + named + "): Stroom's parser takes them "
                           + "out before the Data Splitter reads the text, and so does the dry run. Where they "
                           + "separate fields (a value would run into the next), put a FindReplaceFilter before the "
                           + "parser in the pipeline (find '[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f]', regex true, "
                           + "replacement a space) and build the splitter from the sample as it then reads.");
    }
}
