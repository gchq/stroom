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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// A feed whose source type is read by one reading (a field, a part's meta attribute, or a reading into held
/// values) generates exactly the XSLT it did before source types could be read by a combination of readings: each
/// group's and each whole translation's XSLT is compared, byte for byte, with the one generated before (kept as test
/// resources, written by the generator as it was at 6ecac82594, but for a held value's XML test, which since
/// 2026-11-18 takes a text holding a document type declaration as no XML, as the tools do).
class TestSingleReadingXsltUnchanged extends XsltRuns {

    private static final String COMMON_HREF = "uuid='c0000000-0000-0000-0000-000000000001', name='Held common'";
    private static final String HELPERS_HREF = "uuid='c0000000-0000-0000-0000-000000000002', name='Held helpers'";
    private static final String GOLDEN = "/stroom/ai/impl/tools/generation/single-reading/";
    private static final List<SourceTypeModule.Row> ROWS = List.of(
            new SourceTypeModule.Row("Auth", SourceTypeRows.VALUE, "com.x.Auth"),
            new SourceTypeModule.Row("Mail", SourceTypeRows.PATTERN, "^/var/log/mail/"),
            new SourceTypeModule.Row("Directory", SourceTypeRows.MEMBER, "Directory Services", ";"),
            SourceTypeModule.Row.of(SourceTypeRows.noValue()));
    private static final String MAPPING = """
            {"input": "json", "json_layout": "lines",
             "common": [{"path": "EventTime/TimeCreated", "field": "timestamp"},
                        {"path": "EventSource/System/Name", "value": "Held"},
                        {"path": "EventSource/System/Environment", "value": "Prod"},
                        {"path": "EventSource/Generator", "value": "Held"},
                        {"path": "EventSource/Device/HostName", "field": "hostname"}],
             "events": [{"name": "line", "allow_unknown": "a test",
                         "fields": [{"path": "EventDetail/TypeId", "field": "id"},
                                    {"path": "EventDetail/Unknown/Data", "data_name": "event",
                                     "field": "event"}]}]}""";

    @ParameterizedTest
    @ValueSource(strings = {"sourcetype", "event.Workload", "meta:Feed", "event>logger_name",
            "event>logger_name | event>xml:@logger", "event>payload>#root"})
    void aGroupReadByOneReadingIsWrittenAsBefore(final String field) {
        final TranslationMapping common = commonMapping();
        final XsltGenerator.Generated group = XsltGenerator.generate(groupOf(field, common), SCHEMA, "4.1.0");

        assertThat(group.problems()).isEmpty();
        assertThat(group.xslt()).isEqualTo(golden("group-" + fileName(field)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sourcetype", "meta:Feed", "event>logger_name | event>xml:@logger"})
    void aWholeTranslationWatchingOneReadingIsWrittenAsBefore(final String field) {
        final TranslationMapping mapping = TranslationMapping.fromJson(json(MAPPING)).withSeenSourceTypes(
                SeenSourceTypes.ofRows(field, ROWS.stream().map(SourceTypeModule.Row::toolRow).toList()));
        final XsltGenerator.Generated generated = XsltGenerator.generate(mapping, SCHEMA, "4.1.0");

        assertThat(generated.problems()).isEmpty();
        assertThat(generated.xslt()).isEqualTo(golden("seen-" + fileName(field)));
    }

    private static TranslationMapping commonMapping() {
        return TranslationMapping.fromJson(json("""
                {"input": "json", "json_layout": "lines",
                 "common": [{"path": "EventTime/TimeCreated", "field": "timestamp"},
                            {"path": "EventSource/System/Name", "value": "Held"},
                            {"path": "EventSource/Device/HostName", "field": "hostname"},
                            {"path": "EventSource/System/Environment", "value": "Prod"}],
                 "events": [{"name": "common"}],
                 "module": {"kind": "common", "name": "Held", "mode": "held_common",
                            "helpers": "%s"}}""".formatted(HELPERS_HREF)));
    }

    private static TranslationMapping groupOf(final String field, final TranslationMapping common) {
        final TranslationMapping mapping = TranslationMapping.fromJson(json("""
                {"input": "json", "json_layout": "lines",
                 "common": [{"path": "EventSource/Generator", "value": "Held"}],
                 "events": [{"name": "line", "allow_unknown": "a test",
                             "fields": [{"path": "EventDetail/TypeId", "field": "id"},
                                    {"path": "EventDetail/Unknown/Data", "data_name": "event",
                                     "field": "event"}]}]}"""));
        return mapping.withModule(new SourceTypeModule(SourceTypeModule.GROUP, "Held all", "held_all",
                List.of("Auth", "Mail"), COMMON_HREF, null, SourceTypeModule.commonParts(common), null, field,
                ROWS));
    }

    // A file name for a reading: its characters outside letters, digits and dots made '_'.
    private static String fileName(final String field) {
        return field.replaceAll("[^A-Za-z0-9.]", "_") + ".xsl";
    }

    private static String golden(final String name) {
        try (final InputStream in = TestSingleReadingXsltUnchanged.class.getResourceAsStream(GOLDEN + name)) {
            assertThat(in).as("golden %s", name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
