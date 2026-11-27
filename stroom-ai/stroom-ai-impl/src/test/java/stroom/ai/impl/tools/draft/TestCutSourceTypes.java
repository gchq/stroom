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

import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.SourceTypeRows;
import stroom.ai.impl.tools.sample.SourceTypeScope;
import stroom.ai.impl.tools.sample.ToolJson;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Records of an aggregated feed whose source type field is read inside a string processing cuts (seen in eval case
/// 112 on Haiku, 2026-11-26: the kiosk accounts' failed logons, their `event` past the JSON parser's
/// `stringTruncateLength`, passed over by the group's pipeline as of no source type).
class TestCutSourceTypes {

    private static final int CUT = LocalCheck.JSON_PARSER_CUT;
    private static final String FIELD = "event>appender_ref";

    @Test
    void aLongRecordOfTheGroupWhoseSourceTypeTheCutHidesIsAProblem() {
        final CheckedRecords checked = checked();

        assertThat(CutSourceTypes.problem(checked, scope(FIELD), CUT)).hasValue("the source type field "
                + "event>appender_ref is read from a value longer than stringTruncateLength (10,000 characters) on 1 "
                + "record(s): processing cuts the value there, the source type can't be read from the cut text, and "
                + "the group's pipeline passes those records over untranslated, whatever they hold; keep long values "
                + "whole (long_values=whole, the pipeline's stringTruncateLength raised), or read the source type "
                + "from a field outside the long value (logon 1)");
    }

    @Test
    void nothingIsSaidWhereTheValuesAreKeptWholeOrTheFieldIsOutsideTheLongValue() {
        final CheckedRecords checked = checked();

        // Kept whole: the parser's limit raised past the longest.
        assertThat(CutSourceTypes.problem(checked, scope(FIELD), 20_000)).isEmpty();
        // The source type read from a short field.
        assertThat(CutSourceTypes.problem(checked, scope("host"), CUT)).isEmpty();
        // No scope, or a mapping reading no JSON.
        assertThat(CutSourceTypes.problem(checked, null, CUT)).isEmpty();
    }

    @Test
    void aRecordWhoseSourceTypeTheCutHidesIsLost() {
        final CheckedRecords checked = checked();
        final Map<?, ?> longLogon = (Map<?, ?>) checked.record(1);
        final Map<?, ?> shortLogon = (Map<?, ?>) checked.record(0);

        assertThat(CutSourceTypes.lost(FIELD, longLogon, CutSourceTypes.asCut(checked.mapping(), longLogon, CUT)))
                .isTrue();
        assertThat(CutSourceTypes.lost(FIELD, shortLogon, CutSourceTypes.asCut(checked.mapping(), shortLogon, CUT)))
                .isFalse();
        // The held key read as the flattened record names it: not there once its held field is cut.
        assertThat(CutSourceTypes.lost("event.appender_ref", longLogon,
                CutSourceTypes.asCut(checked.mapping(), longLogon, CUT))).isTrue();
        // A record with no source type read whole has none to lose.
        assertThat(CutSourceTypes.lost(FIELD, Map.of("host", "web-1"), Map.of("host", "web-1"))).isFalse();
        assertThat(CutSourceTypes.lost(null, longLogon, longLogon)).isFalse();
    }

    @Test
    void aLongRecordTheMappingDropsIsNoProblem() {
        // Only the long CATALINA record, which the drop rule drops: passed over, it is untranslated all the same.
        final TranslationMapping mapping = mapping();
        final List<String> lines = List.of(line("SECURITY", "Login", ""), line("CATALINA", "org.apache.Startup",
                "p".repeat(CUT + 50)));
        final CheckedRecords checked = CheckedRecords.of(mapping, LocalCheck.sampleRecords(mapping,
                String.join("\n", lines), null).records());

        assertThat(CutSourceTypes.problem(checked, scope(FIELD), CUT)).isEmpty();
    }

    // A short logon, a long logon (its appender before its long message), a short and a long Tomcat record.
    private static CheckedRecords checked() {
        final TranslationMapping mapping = mapping();
        final List<String> lines = List.of(line("SECURITY", "Login", ""), line("SECURITY", "Login",
                "p".repeat(CUT + 50)), line("CATALINA", "org.apache.Startup", ""), line("CATALINA",
                "org.apache.Startup", "p".repeat(CUT + 50)));
        return CheckedRecords.of(mapping, LocalCheck.sampleRecords(mapping, String.join("\n", lines), null)
                .records());
    }

    // Logons by a contains on the held text (as the agent recognised them), Tomcat's records dropped, the rest kept.
    private static TranslationMapping mapping() {
        final Map<String, Object> json = new LinkedHashMap<>();
        json.put("input", "json");
        json.put("json_fields", List.of("event"));
        json.put("drop_when", List.of(Map.of("reason", "tomcat", "when", List.of(Map.of("field", "event",
                "contains", "org.apache.")))));
        json.put("events", List.of(Map.of("name", "logon", "when", List.of(Map.of("field", "event", "contains",
                        "Login")), "fields", List.of(Map.of("path", "EventDetail/TypeId", "value", "Logon"))),
                Map.of("name", "rest", "catch_all", true, "allow_unknown", "the rest",
                        "fields", List.of(Map.of("path", "EventDetail/TypeId", "value", "Unknown")))));
        return TranslationMapping.parse(json);
    }

    private static SourceTypeScope scope(final String field) {
        return new SourceTypeScope("b", field, List.of(new SourceTypeRows.Row("SECURITY", SourceTypeRows.VALUE,
                "SECURITY"), new SourceTypeRows.Row("CATALINA", SourceTypeRows.VALUE, "CATALINA"),
                new SourceTypeRows.Row("web", SourceTypeRows.VALUE, "web-1")), List.of("SECURITY", "CATALINA",
                "web"));
    }

    // A line whose event holds its appender and logger first, then a message padded as given.
    private static String line(final String appender, final String logger, final String padding) {
        final Map<String, Object> event = new LinkedHashMap<>();
        event.put("appender_ref", appender);
        event.put("logger_name", logger);
        event.put("message", logger + " " + padding);
        final Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("host", "web-1");
        outer.put("event", ToolJson.dumps(event));
        return ToolJson.dumps(outer);
    }
}
