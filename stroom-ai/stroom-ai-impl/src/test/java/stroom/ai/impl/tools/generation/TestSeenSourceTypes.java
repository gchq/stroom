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

import stroom.ai.impl.tools.sample.SourceTypeRows;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A whole translation of a feed whose survey confirmed a source type field: the values seen kept with its mapping,
/// and a value outside them logged (INFO) once a stream, normalised as the tools read it; a translation without
/// them writes no such check.
class TestSeenSourceTypes extends XsltRuns {

    private static final String MAPPING = """
            {"input": "json", "json_layout": "lines",
             "common": [{"path": "EventTime/TimeCreated", "field": "timestamp"},
                        {"path": "EventSource/System/Name", "value": "Azure"},
                        {"path": "EventSource/System/Environment", "value": "Prod"},
                        {"path": "EventSource/Generator", "value": "Azure"},
                        {"path": "EventSource/Device/HostName", "value": "m365"}],
             "ignored_fields": ["Workload"],
             "events": [{"name": "op", "allow_unknown": "a test",
                         "fields": [{"path": "EventDetail/TypeId", "field": "Operation"},
                                    {"path": "EventDetail/Unknown/Data", "data_name": "user",
                                     "field": "UserId"}]}]}""";

    @Test
    void aValueTheSurveyDidNotSeeIsLoggedOnceAStreamAndItsRecordsStillTranslated() {
        final TranslationMapping mapping = TranslationMapping.fromJson(json(MAPPING)).withSeenSourceTypes(
                new SeenSourceTypes("Workload", List.of("ExchangeOnline", "SharePoint"), List.of("^Azure")));
        final XsltGenerator.Generated generated = XsltGenerator.generate(mapping, SCHEMA, "4.1.0");
        assertThat(generated.problems()).isEmpty();

        final List<String> logged = new ArrayList<>();
        final XmlElement events = transform(generated.xslt(), records(), Map.of(), logged);

        assertThat(invalid(events, XSD)).isEmpty();
        // Every record translated by the rules, the unseen ones too.
        assertThat(children(events, "Event")).hasSize(8);
        // PowerBI twice (padded once) and Teams once: one line each; a value a pattern covers, a nil value and no
        // value are no new source type.
        assertThat(logged).filteredOn(line -> line.contains(XsltGenerator.UNSEEN_SOURCE_TYPE))
                .containsExactlyInAnyOrder(unseen("PowerBI"), unseen("Teams"));
    }

    @Test
    void theValuesSeenAreKeptWithTheMappingAndAMappingWithoutThemIsWrittenAsBefore() {
        final TranslationMapping plain = TranslationMapping.fromJson(json(MAPPING));
        final TranslationMapping watched = plain.withSeenSourceTypes(SeenSourceTypes.ofRows("Workload", List.of(
                new SourceTypeRows.Row("Exchange", SourceTypeRows.VALUE, "ExchangeOnline"),
                new SourceTypeRows.Row("Azure", SourceTypeRows.PATTERN, "^Azure"))));

        final TranslationMapping read = TranslationMapping.fromJson(watched.toJson(DumpMode.COMPACT));

        assertThat(read.seenSourceTypes()).isEqualTo(new SeenSourceTypes("Workload", List.of("ExchangeOnline"),
                List.of("^Azure")));
        assertThat(plain.toJson(DumpMode.COMPACT)).doesNotContainKey("seen_source_types");
        assertThat(XsltGenerator.generate(plain, SCHEMA, "4.1.0").xslt()).doesNotContain(
                XsltGenerator.UNSEEN_SOURCE_TYPE, "source-type");
        assertThat(watched.seenSourceTypes().seen("AzureActiveDirectory")).isTrue();
        assertThat(watched.seenSourceTypes().seen("")).isTrue();
        assertThat(watched.seenSourceTypes().seen("PowerBI")).isFalse();
    }

    private static String unseen(final String value) {
        return "INFO " + XsltGenerator.UNSEEN_SOURCE_TYPE + value + XsltGenerator.UNSEEN_SOURCE_TYPE_IN + "Workload"
               + XsltGenerator.UNSEEN_SOURCE_TYPE_END;
    }

    // JSON lines as Stroom's JSON parser gives them, its root map around the records.
    private static String records() {
        final StringBuilder out = new StringBuilder("<map xmlns=\"http://www.w3.org/2013/XSL/json\">");
        out.append(record("ExchangeOnline"));
        out.append(record("SharePoint"));
        out.append(record("AzureActiveDirectory"));
        out.append(record("PowerBI"));
        out.append(record(" PowerBI\n"));
        out.append(record("Teams"));
        out.append(record("n/a"));
        out.append(record(""));
        return out.append("</map>").toString();
    }

    private static String record(final String workload) {
        return "<map><string key=\"timestamp\">2026-11-10T00:00:00.000Z</string><string key=\"Workload\">"
               + workload + "</string><string key=\"Operation\">Op</string><string key=\"UserId\">bob</string>"
               + "</map>";
    }
}
