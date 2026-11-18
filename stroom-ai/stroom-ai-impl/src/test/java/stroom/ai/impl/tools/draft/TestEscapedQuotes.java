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

import stroom.ai.impl.tools.sample.SplitterSpec;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Key=value values holding a quote escaped with a backslash, read with `transform: unescape_backslashes` wherever
/// the splitter spec holds the key_value spec; delimited values holding a doubled quote, read with
/// `transform: unescape_quotes` wherever it holds the delimited spec.
class TestEscapedQuotes {

    // A syslog record whose key=value body holds an escaped quote; its header's structured data and its body's text
    // hold it too.
    private static final List<Map<String, Object>> RECORDS = List.of(
            Map.of("structured", "[x@1 a=\"b\\\"c\"]", "message", "msg=\"say \\\"hi\\\"\" user=alice",
                    "msg", "say \\\"hi\\\"", "user", "alice"),
            Map.of("structured", "-", "message", "msg=\"ok\" user=bob", "msg", "ok", "user", "bob"));
    // A syslog record whose delimited body holds a doubled quote; its header's structured data and its body's text
    // hold one too.
    private static final List<Map<String, Object>> DOUBLED = List.of(
            Map.of("structured", "[x@1 a=\"b\"\"c\"]", "message", "alice,\"say \"\"hi\"\"\"",
                    "msg", "say \"\"hi\"\"", "user", "alice"),
            Map.of("structured", "-", "message", "bob,ok", "msg", "ok", "user", "bob"));

    @Test
    void aTopLevelKeyValueSpecsEscapedValuesAreUnescaped() {
        final Map<String, Object> mapping = mapping("msg", "user");
        EscapedQuotes.unescape(SplitterSpec.fromMap(Map.of("kind", "key_value", "quote", "\"")), RECORDS, mapping,
                new ArrayList<>());

        assertThat(transforms(mapping)).containsEntry("msg", "unescape_backslashes").containsEntry("user", null);
    }

    @Test
    void aSyslogBodysEscapedValuesAreUnescapedButNotTheHeadersOrTheBodysText() {
        // Seen in the agent eval (case 25): {kind: syslog, rfc: rfc5424, body: {kind: key_value, quote: '"'}} and
        // msg="say \"hi\"" read with no transform.
        final Map<String, Object> mapping = mapping("msg", "user", "structured", "message");
        final List<String> notes = new ArrayList<>();
        EscapedQuotes.unescape(SplitterSpec.fromMap(Map.of("kind", "syslog", "rfc", "rfc5424", "body",
                Map.of("kind", "key_value", "quote", "\""))), RECORDS, mapping, notes);

        assertThat(transforms(mapping)).containsEntry("msg", "unescape_backslashes")
                .containsEntry("user", null)
                .containsEntry("structured", null)
                .containsEntry("message", null);
        assertThat(notes).singleElement().asString().startsWith("['msg'] hold quotes escaped with a backslash");
    }

    @Test
    void anAlternativesPartsEscapedValuesAreUnescapedThoughItsBodyIsNested() {
        final Map<String, Object> mapping = mapping("msg");
        final Map<String, Object> syslogPart = Map.of("kind", "syslog", "rfc", "rfc5424", "variant", "syslog",
                "body", Map.of("kind", "key_value", "quote", "\""));
        final Map<String, Object> pairsPart = Map.of("kind", "key_value", "quote", "'", "variant", "pairs");
        EscapedQuotes.unescape(SplitterSpec.fromMap(Map.of("kind", "alternatives", "variant_field", "format",
                "parts", List.of(pairsPart, syslogPart))), RECORDS, mapping, new ArrayList<>());

        assertThat(transforms(mapping)).containsEntry("msg", "unescape_backslashes");
    }

    @Test
    void aKeyValueSpecWithNoQuoteOrNoEscapeLeavesTheMappingAlone() {
        final Map<String, Object> unquoted = mapping("msg");
        final List<String> notes = new ArrayList<>();
        EscapedQuotes.unescape(SplitterSpec.fromMap(Map.of("kind", "syslog", "rfc", "rfc5424", "body",
                Map.of("kind", "key_value"))), RECORDS, unquoted, notes);
        assertThat(transforms(unquoted)).containsEntry("msg", null);

        final Map<String, Object> plain = mapping("msg");
        EscapedQuotes.unescape(SplitterSpec.fromMap(Map.of("kind", "key_value", "quote", "\"")),
                List.of(Map.of("msg", "ok")), plain, notes);
        assertThat(transforms(plain)).containsEntry("msg", null);
        assertThat(notes).isEmpty();
    }

    @Test
    void aTopLevelDelimitedSpecsDoubledQuotesAreUndoubled() {
        final Map<String, Object> mapping = mapping("msg", "user");
        final List<String> notes = new ArrayList<>();
        EscapedQuotes.undouble(SplitterSpec.fromMap(Map.of("kind", "delimited", "quote", "\"", "header", true)),
                DOUBLED, mapping,
                notes);

        // At the top level every field is the spec's own: those of the records holding a doubled quote too.
        assertThat(transforms(mapping)).containsEntry("msg", "unescape_quotes").containsEntry("user", null);
        assertThat(notes).singleElement().asString().startsWith("['message', 'msg', 'structured'] hold doubled "
                                                                + "quotes");
    }

    @Test
    void aSyslogBodysDoubledQuotesAreUndoubledButNotTheHeadersOrTheBodysText() {
        final Map<String, Object> mapping = mapping("msg", "user", "structured", "message");
        EscapedQuotes.undouble(SplitterSpec.fromMap(Map.of("kind", "syslog", "rfc", "rfc5424", "body",
                Map.of("kind", "delimited", "quote", "\"", "header", List.of("user", "msg")))), DOUBLED, mapping,
                new ArrayList<>());

        assertThat(transforms(mapping)).containsEntry("msg", "unescape_quotes")
                .containsEntry("user", null)
                .containsEntry("structured", null)
                .containsEntry("message", null);
    }

    @Test
    void anAlternativesPartsBodysDoubledQuotesAreUndoubled() {
        final Map<String, Object> mapping = mapping("msg");
        final Map<String, Object> syslogPart = Map.of("kind", "syslog", "rfc", "rfc5424", "variant", "syslog",
                "body", Map.of("kind", "delimited", "quote", "\"", "header", List.of("user", "msg")));
        final Map<String, Object> pairsPart = Map.of("kind", "key_value", "quote", "\"", "variant", "pairs");
        EscapedQuotes.undouble(SplitterSpec.fromMap(Map.of("kind", "alternatives", "variant_field", "format",
                "parts", List.of(pairsPart, syslogPart))), DOUBLED, mapping, new ArrayList<>());

        assertThat(transforms(mapping)).containsEntry("msg", "unescape_quotes");
    }

    @Test
    void anUnquotedDelimitedSpecOrAKeyValueOneLeavesDoubledQuotesAlone() {
        final List<String> notes = new ArrayList<>();
        final Map<String, Object> unquoted = mapping("msg");
        EscapedQuotes.undouble(SplitterSpec.fromMap(Map.of("kind", "syslog", "rfc", "rfc5424", "body",
                Map.of("kind", "delimited", "header", List.of("user", "msg")))), DOUBLED, unquoted, notes);
        assertThat(transforms(unquoted)).containsEntry("msg", null);

        final Map<String, Object> pairs = mapping("msg");
        EscapedQuotes.undouble(SplitterSpec.fromMap(Map.of("kind", "key_value", "quote", "\"")), DOUBLED, pairs,
                notes);
        assertThat(transforms(pairs)).containsEntry("msg", null);
        EscapedQuotes.undouble(null, DOUBLED, pairs, notes);
        assertThat(notes).isEmpty();
    }

    // A mapping whose common entries read each field, and one rule reading the first again.
    private static Map<String, Object> mapping(final String... fields) {
        final List<Map<String, Object>> common = new ArrayList<>();
        for (final String field : fields) {
            common.add(entry("EventDetail/Data", field));
        }
        final Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("name", "rule");
        rule.put("fields", new ArrayList<>(List.of(entry("EventDetail/Description", fields[0]))));
        final Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("common", common);
        mapping.put("events", new ArrayList<>(List.of(rule)));
        return mapping;
    }

    private static Map<String, Object> entry(final String path, final String field) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("path", path);
        entry.put("field", field);
        return entry;
    }

    // Each common entry's field and its transform (null for none); the rule's entry must agree with its common one.
    @SuppressWarnings("unchecked")
    private static Map<String, Object> transforms(final Map<String, Object> mapping) {
        final Map<String, Object> transforms = new LinkedHashMap<>();
        ((List<Map<String, Object>>) mapping.get("common")).forEach(entry ->
                transforms.put(String.valueOf(entry.get("field")), entry.get("transform")));
        final Map<String, Object> ruleEntry = ((List<Map<String, Object>>) ((List<Map<String, Object>>)
                mapping.get("events")).getFirst().get("fields")).getFirst();
        assertThat(ruleEntry.get("transform")).isEqualTo(transforms.get(String.valueOf(ruleEntry.get("field"))));
        return transforms;
    }
}
