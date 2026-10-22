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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The Stroom MCP server's tests of hand edits that are about [HandEdits] itself (`tests/test_hand_edits.py`; its
/// other tests are of the save tools, ported with them). The XSLTs are the server's indexing generator's, for the
/// tests' plans, kept in `store_cases.json`.
class TestHandEdits {

    private static final List<Map<String, Object>> CASES = GenerationFixtures.json(GenerationFixtures.resource(
            "store_cases.json"));

    @SuppressWarnings("unchecked")
    private static String xslt(final String name) {
        return ((Map<String, String>) CASES.getFirst().get("result")).get(name);
    }

    @Test
    void howAnEditIsWrittenDoesNotCount() {
        // Brackets round a whole test, whitespace, xsl:if or not: the same fields from the same elements.
        final String generated = xslt("index_bytes");
        final String restyled = generated.replace("test=\"EventDetail/*/Action\"",
                "test=\"( EventDetail/*/Action )\"");
        assertThat(HandEdits.outline(generated).reads()).isEqualTo(HandEdits.outline(restyled).reads());
        // A template's field, read relative to its element, reads the same path.
        assertThat(HandEdits.lost("<x/>", "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
                                          + "<xsl:value-of select=\"EventDetail/View/Resource/URL\"/></xsl:stylesheet>",
                "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"><xsl:template match=\"Resource\">"
                + "<xsl:value-of select=\"URL\"/></xsl:template></xsl:stylesheet>")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aSourceChangedByHandInAVariableIsItsFields() {
        // The indexing XSLT reads an input its template reads often into a variable, as the Events translation
        // does: a source changed there is the change of the fields reading the variable, and asked about as theirs.
        final String saved = xslt("index_saved");
        assertThat(saved).contains("<xsl:variable name=\"http_request_method\" "
                                   + "select=\"EventDetail/*/Resource/HTTPMethod\"/>");
        final String edited = saved.replaceFirst(java.util.regex.Pattern.quote(
                "select=\"EventDetail/*/Resource/HTTPMethod\"/>"), java.util.regex.Matcher.quoteReplacement(
                "select=\"upper-case(EventDetail/*/Resource/HTTPMethod)\"/>"));
        assertThat(edited).isEqualTo(xslt("index_var_edited"));
        final List<Object> args = (List<Object>) CASES.stream()
                .filter(c -> "collisions".equals(c.get("call")) && "index".equals(((List<Object>) c.get("args"))
                        .getFirst()))
                .findFirst()
                .orElseThrow()
                .get("args");
        final List<HandEdits.Undone> items = HandEdits.undone(saved, edited, xslt("index_proposed"));
        final Map<String, HandEdits.Collision> clashes = HandEdits.collisions("index",
                (Map<String, Object>) args.get(1), (Map<String, Object>) args.get(2), items);
        assertThat(clashes.keySet()).containsExactly("http.request.method");
    }

    @Test
    void variablesOrNoneTheSameReads() {
        // An XSLT the generator wrote before it read inputs into variables, edited by hand since: regenerated now,
        // with variables, it is checked for the edit alone, not for the reads the variables now hold.
        final String now = xslt("index_saved");
        final String old = xslt("index_old");
        assertThat(old).doesNotContain("<xsl:variable");
        assertThat(now).contains("<xsl:variable");
        final String edited = old.replaceFirst(java.util.regex.Pattern.quote("<string key=\"method\">"),
                "<string key=\"verb\">");
        assertThat(HandEdits.lost(now, old, now)).isEmpty();
        assertThat(HandEdits.lost(now, edited, now)).containsExactly("no longer writes string verb",
                "writes string method again");
    }

    @Test
    void aTranslationEditedByHandIsNoLongerWrittenByTheRegeneratedCode() {
        // From test_a_translation_edited_by_hand_is_kept_through_build_translation_xslt: a Data added by hand to the
        // generated translation is what regenerating it would undo.
        assertThat(HandEdits.lost(xslt("translation"), xslt("translation_dc"), xslt("translation")))
                .contains("no longer writes Data device_class");
        // test_a_constant_changed_by_hand_and_in_the_mapping_is_asked_about: the constant changed by hand, and the
        // mapping's change to the same field, collide.
        final List<String> lost = HandEdits.lost(xslt("translation"), xslt("translation_edited"),
                xslt("translation_prod"));
        assertThat(lost).contains("no longer writes Environment = Test");
    }
}
