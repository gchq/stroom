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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.catalogue.Catalogue;
import stroom.ai.impl.tools.catalogue.CatalogueCheck;
import stroom.ai.impl.tools.catalogue.CatalogueSample;
import stroom.ai.impl.tools.catalogue.CatalogueSeed;
import stroom.ai.impl.tools.generation.Extraction;
import stroom.ai.impl.tools.generation.TranslationMapping;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TestPairMatches {

    @Test
    void theFirstMatchIsKeptByRegexFlagsAndText() {
        final PairMatches matches = PairMatches.create();
        final Pattern pattern = ToolText.re("user=(\\w+)(?: op=(\\w+))?");

        assertThat(matches.firstMatch(pattern, "user=alice op=login user=bob", true))
                .containsExactly("alice", "login");
        // A group not in the match is null; a text the regex doesn't match has none.
        assertThat(matches.firstMatch(pattern, "user=carol", false)).containsExactly("carol", null);
        assertThat(matches.firstMatch(pattern, "nothing here", true)).isNull();
        assertThat(matches.entries()).isEqualTo(3);

        // The same text read again (another instance of it) is found, timed or not.
        final String again = new String("user=alice op=login user=bob".toCharArray());
        assertThat(matches.firstMatch(pattern, again, false)).containsExactly("alice", "login");
        assertThat(matches.firstMatch(pattern, "nothing here", false)).isNull();
        assertThat(matches.entries()).isEqualTo(3);

        // The same regex with other flags is another regex.
        final Pattern ignoringCase = ToolText.re("USER=(\\w+)", Pattern.CASE_INSENSITIVE);
        final Pattern matchingCase = ToolText.re("USER=(\\w+)");
        assertThat(matches.firstMatch(ignoringCase, "user=dave", true)).containsExactly("dave");
        assertThat(matches.firstMatch(matchingCase, "user=dave", true)).isNull();
        assertThat(matches.entries()).isEqualTo(5);
    }

    @Test
    void pastTheCapMatchesAreWorkedOutWithoutBeingKept() {
        final Pattern pattern = ToolText.re("id=(\\d+)");

        final PairMatches fewEntries = PairMatches.create(2, PairMatches.MAX_CHARACTERS);
        assertThat(fewEntries.firstMatch(pattern, "id=1", true)).containsExactly("1");
        assertThat(fewEntries.firstMatch(pattern, "id=2", true)).containsExactly("2");
        assertThat(fewEntries.firstMatch(pattern, "id=3", true)).containsExactly("3");
        assertThat(fewEntries.firstMatch(pattern, "id=3", true)).containsExactly("3");
        assertThat(fewEntries.entries()).isEqualTo(2);

        // A text longer than the characters left isn't kept; a short one still is.
        final PairMatches fewCharacters = PairMatches.create(PairMatches.MAX_ENTRIES, 40);
        final String longText = "id=4 " + "x".repeat(50);
        assertThat(fewCharacters.firstMatch(pattern, longText, false)).containsExactly("4");
        assertThat(fewCharacters.entries()).isZero();
        assertThat(fewCharacters.characters()).isZero();
        assertThat(fewCharacters.firstMatch(pattern, "id=5", false)).containsExactly("5");
        assertThat(fewCharacters.entries()).isEqualTo(1);
        assertThat(fewCharacters.characters()).isLessThanOrEqualTo(40);
        assertThat(fewCharacters.firstMatch(pattern, longText, false)).containsExactly("4");

        // None keeps nothing.
        assertThat(PairMatches.none().firstMatch(pattern, "id=6", true)).containsExactly("6");
        assertThat(PairMatches.none().entries()).isZero();
    }

    @Test
    void pairsInAJsonFieldAreTheSameWithMatchesKeptAsWithout() {
        final List<String> lines = new ArrayList<>();
        for (int n = 0; n < 12; n++) {
            lines.add("{\"time\":\"2026-11-20T10:00:0" + (n % 10) + "Z\",\"host\":\"h" + (n % 3)
                      + "\",\"msg\":\"user=u" + (n % 4) + " action=\\\"log " + (n % 2 == 0
                    ? "in"
                    : "out") + "\\\" src=10.0.0." + n + " result=" + (n % 3 == 0
                    ? "fail"
                    : "ok") + "\"}");
        }
        assertAlike(List.of(String.join("\n", lines)), null);
    }

    @Test
    void colonAndDashedPairsAreTheSameWithMatchesKeptAsWithout() {
        final List<String> lines = new ArrayList<>();
        for (int n = 0; n < 12; n++) {
            lines.add("{\"time\":\"2026-11-20T10:00:0" + (n % 10) + "Z\",\"msg\":\"SessionId: " + (2600 + n)
                      + " - User: u" + (n % 4) + " - Action: " + (n % 2 == 0
                    ? "login"
                    : "logout") + " - Result: ok\"}");
        }
        assertAlike(List.of(String.join("\n", lines)), null);
    }

    @Test
    void pairsInHeldJsonAreTheSameWithMatchesKeptAsWithout() {
        final List<String> lines = new ArrayList<>();
        for (int n = 0; n < 12; n++) {
            lines.add("{\"time\":\"2026-11-20T10:00:0" + (n % 10) + "Z\",\"event\":\"{\\\"logger\\\":\\\"auth"
                      + (n % 2) + "\\\",\\\"detail\\\":\\\"user=u" + (n % 4) + " op=" + (n % 2 == 0
                    ? "login"
                    : "logout") + " code=" + n + "\\\"}\"}");
        }
        assertAlike(List.of(String.join("\n", lines)), null);
    }

    @Test
    void pairsInXmlAreTheSameWithMatchesKeptAsWithout() {
        final StringBuilder xml = new StringBuilder();
        for (int n = 0; n < 12; n++) {
            xml.append("<Event><Time>2026-11-20T10:00:0").append(n % 10).append("Z</Time><Message>user=u")
                    .append(n % 4).append(" op=").append(n % 2 == 0
                            ? "login"
                            : "logout").append(" code=").append(n).append("</Message></Event>\n");
        }
        assertAlike(List.of(xml.toString()), null);
    }

    @Test
    void pairsInRepeatedItemsAreTheSameWithMatchesKeptAsWithout() {
        final List<String> lines = new ArrayList<>();
        for (int n = 0; n < 8; n++) {
            lines.add("{\"time\":\"2026-11-20T10:00:0" + n + "Z\",\"batch\":" + n + ",\"items\":["
                      + "{\"msg\":\"user=u" + (n % 3) + " op=login code=" + n + "\"},"
                      + "{\"msg\":\"user=u" + (n % 2) + " op=logout code=" + (n + 10) + "\"}]}");
        }
        assertAlike(List.of(String.join("\n", lines)), "items");
    }

    @Test
    void extractedValuesAreTheSameWithMatchesKeptAsWithout() {
        final List<Extraction> extractions = List.of(
                Extraction.fromJson(Map.of("field", "msg", "regex", "user=(\\S+)", "names", List.of("user"))),
                Extraction.fromJson(Map.of("field", "msg", "regex", "(?:op|action)=(\\S+)(?: code=(\\d+))?",
                        "names", List.of("op", "code"))),
                // Read from what an earlier extraction gives.
                Extraction.fromJson(Map.of("field", "user", "regex", "^u(\\d)", "names", List.of("user_n"))),
                Extraction.fromJson(Map.of("field", "msg", "regex", "OP=(\\w+)", "flags", "i",
                        "names", List.of("op_any_case"))));
        final PairMatches matches = PairMatches.create();
        for (final String msg : List.of("user=u1 op=login code=7", "user=u2 action=logout", "nothing", "")) {
            final Map<String, String> without = LocalCheck.extractedValues(extractions, Map.of("msg", msg),
                    PairMatches.none());
            // Twice with the matches kept: worked out, then found.
            assertThat(LocalCheck.extractedValues(extractions, Map.of("msg", msg), matches)).isEqualTo(without);
            assertThat(LocalCheck.extractedValues(extractions, Map.of("msg", new String(msg.toCharArray())),
                    matches)).isEqualTo(without);
        }
        assertThat(matches.entries()).isPositive();
    }

    // A draft, and the catalogue's check of the draft's event types over the records as the draft reads them, made
    // once with every part sharing one call's matches and once keeping none: the same.
    private static void assertAlike(final List<Object> samples, final String forEach) {
        final PairMatches shared = PairMatches.create();
        final Map<String, Object> kept = DraftMapping.draft(samples, "", null, null, null, null, null, forEach,
                shared);
        final Map<String, Object> notKept = DraftMapping.draft(samples, "", null, null, null, null, null, forEach,
                PairMatches.none());
        assertThat(kept).isEqualTo(notKept);
        final Map<String, Object> mapping = JsonValues.map(kept.get("mapping"));
        assertThat(JsonValues.maps(mapping.get("extract"))).as("the draft reads pairs").isNotEmpty();
        assertThat(shared.entries()).isPositive();

        final TranslationMapping reading = reading(mapping, forEach);
        final Catalogue catalogue = new Catalogue(CatalogueSeed.fromDraft(kept), null, null);
        final CatalogueSample sharedSample = CatalogueSample.read(reading, samples, null,
                LocalCheck.JSON_PARSER_CUT, null, shared);
        final CatalogueSample ownSample = CatalogueSample.read(reading, samples, null, LocalCheck.JSON_PARSER_CUT,
                null, PairMatches.none());
        // Checked twice over the shared matches (a call checks before and after a change): alike each time.
        final Map<String, Object> without = CatalogueCheck.check(catalogue, ownSample, null).toJson();
        assertThat(CatalogueCheck.check(catalogue, sharedSample, null).toJson()).isEqualTo(without);
        assertThat(CatalogueCheck.check(catalogue, sharedSample, null).toJson()).isEqualTo(without);
        assertThat(CatalogueCheck.examples(sharedSample, catalogue.eventTypes(), 3))
                .isEqualTo(CatalogueCheck.examples(ownSample, catalogue.eventTypes(), 3));
    }

    // How the draft reads a record (its rules aside), as catalogue_event_types reads the records it checks.
    private static TranslationMapping reading(final Map<String, Object> mapping, final String forEach) {
        final Map<String, Object> json = new LinkedHashMap<>();
        for (final String key : List.of("input", "record", "xml_namespace", "json_fields", "xml_fields",
                "nil_values", "extract", "for_each")) {
            if (mapping.get(key) != null) {
                json.put(key, mapping.get(key));
            }
        }
        if (forEach != null) {
            json.putIfAbsent("for_each", forEach);
        }
        json.put("events", List.of(Map.of("name", "check", "allow_unknown", "a check",
                "fields", List.of(Map.of("path", "EventDetail/TypeId", "value", "Check")))));
        return TranslationMapping.parse(json);
    }
}
