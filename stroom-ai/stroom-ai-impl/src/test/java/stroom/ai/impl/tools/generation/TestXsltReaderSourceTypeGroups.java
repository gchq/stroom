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
import stroom.ai.impl.tools.validation.XsltVersionHistory;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Reading a source type group's XSLT back (`rebuild_mapping`): its rules read as its rules, never the generator's
/// helper templates, which a group names its own (its mode before the name). Seen in the module hand-edit e2e: a
/// group of one rule with no condition (no xsl:choose, its record template calling the group's own check of fields
/// no rule reads before its rule) read that check as its rule, "no Event written", and kept no entry.
class TestXsltReaderSourceTypeGroups extends XsltRuns {

    private static final String COMMON_HREF = "uuid='c0000000-0000-0000-0000-000000000001', name='PEGA common'";
    private static final String HELPERS_HREF = "uuid='c0000000-0000-0000-0000-000000000002', name='PEGA helpers'";
    private static final String MODE = "pega_integration";
    private static final String NO_EVENT = "no Event written";
    private static final String CALL = """
            {"name": "call", "allow_unknown": "integration calls name no action of the schema's",
             "fields": [{"path": "EventDetail/TypeId", "field": "sourcetype"},
                        {"path": "EventDetail/Unknown/Data", "data_name": "message", "field": "message"}]}""";
    private static final String LOGIN = """
            {"name": "login", "when": [{"field": "message", "equals": "login"}],
             "fields": [{"path": "EventDetail/TypeId", "value": "Login"},
                        {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                        {"path": "EventDetail/Authenticate/User/Id", "field": "sourcetype"}]}""";
    private static final String DROP = """
            {"name": "noise", "when": [{"field": "message", "equals": "noise"}], "drop": true}""";
    private static final String OTHER = """
            {"name": "other",
             "fields": [{"path": "EventDetail/TypeId", "field": "message"},
                        {"path": "EventDetail/Unknown/Data", "data_name": "message", "field": "message"}]}""";
    private static final List<String> NAMINGS = List.of("snake_case", "camelCase", "PascalCase", "kebab-case");

    @Test
    void aGroupOfOneRuleIsReadAsItsRuleNotItsCheckOfUnreadFields() {
        // The PEGA integration group: one rule, no condition, its SOAP body an extracted XML field, the common
        // module's parts called.
        final TranslationMapping integration = TranslationMapping.fromJson(json("""
                {"input": "json", "json_layout": "lines", "xml_fields": ["soap"],
                 "extract": [{"field": "message", "regex": "^(Request|Response): (.*)$", "names": ["direction", "soap"],
                              "flags": "s"}],
                 "common": [{"path": "EventSource/Generator", "value": "Pega"}],
                 "ignored_fields": ["app"],
                 "events": [{"name": "call", "allow_unknown": "integration calls name no action of the schema's",
                             "fields": [{"path": "EventDetail/TypeId", "field": "direction"},
                                        {"path": "EventDetail/Unknown/Data", "data_name": "correlation",
                                         "field": "soap/Envelope/Header/CorrelationId"}]}]}""")).withModule(
                module(SourceTypeModule.commonParts(commonMapping())));
        final String code = codeOf(integration);
        // The shape that was misread: the rules chosen by no xsl:choose, the record template calling the group's
        // own helper before its rule.
        assertThat(code).contains("<xsl:call-template name=\"" + MODE + "_" + XsltGenerator.UNREAD_TEMPLATE
                                  + "\"/>\n    <xsl:apply-templates select=\".\" mode=\"" + MODE
                                  + "_event_type_call\"/>");

        final XsltReading reading = XsltReader.read(code);
        assertThat(reading.rules()).singleElement().satisfies(rule -> {
            assertThat(rule.name()).isEqualTo("call");
            assertThat(rule.key()).isEqualTo(MODE + "_event_type_call");
            assertThat(rule.notes()).doesNotContain(NO_EVENT);
            assertThat(rule.leaves()).isNotEmpty();
        });

        final RebuiltMapping kept = XsltReader.rebuild(code, integration, code);
        assertThat(kept.problems()).as("%s", kept.summary()).isEmpty();
        assertThat(kept.notes()).as("%s", kept.summary()).noneMatch(note -> note.contains(NO_EVENT));
        assertThat(kept.added()).isEmpty();
        assertThat(kept.removed()).isEmpty();
        assertThat(kept.reused()).isEqualTo(entries(integration));
        assertThat(kept.mapping().events()).singleElement().satisfies(rule -> assertThat(rule.name())
                .isEqualTo("call"));
        assertThat(kept.mapping().module()).isEqualTo(integration.module());
        assertThat(normalise(codeOf(kept.mapping()))).isEqualTo(normalise(code));

        // Lost, read with the modules it imports: the rule read by its own name.
        final XsltGenerator.Generated common = XsltGenerator.generate(commonMapping(), SCHEMA, "4.1.0");
        assertThat(common.problems()).isEmpty();
        final RebuiltMapping lost = XsltReader.rebuild(code, null, null, Map.of(COMMON_HREF, common.xslt(),
                HELPERS_HREF, HelpersXslt.generate()));
        assertThat(lost.problems()).as("%s", lost.summary()).isEmpty();
        assertThat(lost.notes()).as("%s", lost.summary()).noneMatch(note -> note.contains(NO_EVENT));
        assertThat(lost.mapping().events()).singleElement().satisfies(rule -> {
            assertThat(rule.name()).isEqualTo("call");
            assertThat(rule.fields()).extracting(FieldMapping::path)
                    .contains("EventDetail/TypeId", "EventDetail/Unknown/Data");
        });
    }

    @TestFactory
    Stream<DynamicTest> aGroupsRulesReadBackWholeWhateverItsShapeAndNaming() {
        // Each shape of few rules, in each naming, as a group's XSLT and as a whole translation's: every entry kept
        // and the XSLT regenerated as it was; read lost, its rules by their own names, none a helper template, and a
        // catch-all marked so.
        final Map<String, Shape> shapes = new LinkedHashMap<>();
        shapes.put("one rule", new Shape(null, List.of(CALL), List.of("call")));
        shapes.put("one rule with a condition", new Shape(null, List.of(LOGIN), List.of("login")));
        shapes.put("one rule and a catch-all", new Shape(null, List.of(LOGIN, OTHER), List.of("login", "other")));
        shapes.put("a drop rule and one rule", new Shape(null, List.of(DROP, CALL), List.of("noise", "call")));
        shapes.put("one rule and a drop rule", new Shape(null, List.of(LOGIN, DROP), List.of("login", "noise")));
        shapes.put("one rule for each item", new Shape("items", List.of(CALL), List.of("call")));
        final List<DynamicTest> tests = new ArrayList<>();
        for (final Map.Entry<String, Shape> shape : shapes.entrySet()) {
            for (final String naming : NAMINGS) {
                for (final boolean group : List.of(true, false)) {
                    final String name = shape.getKey() + ", " + naming + (group
                            ? ", a group"
                            : ", a whole translation");
                    tests.add(DynamicTest.dynamicTest(name, () -> assertReadBack(shape.getValue(), naming, group)));
                }
            }
        }
        return tests.stream();
    }

    @Test
    void theGeneratorsHelperTemplatesAreToldByTheirOwnNamesInEveryNaming() {
        for (final String helper : List.of(XsltGenerator.UNREAD_TEMPLATE, XsltGenerator.LOG_KIND_TEMPLATE)) {
            assertThat(XsltReader.helperName(helper, helper)).isTrue();
            for (final String naming : NAMINGS) {
                // As the generator names whatever a group declares: its mode before the name, in its naming.
                final String own = XsltNames.styleName(MODE + "-" + helper, naming);
                assertThat(XsltReader.helperName(own, helper)).as(own).isTrue();
            }
        }
        // A rule's mode, a shared template's name and the common module's parts are no helper of the generator's.
        for (final String other : List.of(MODE + "_event_type_call", "eventMeta", "pega_common_system",
                "unread_fields", "mcp_unread_fields_too")) {
            assertThat(XsltReader.helperName(other, XsltGenerator.UNREAD_TEMPLATE)).as(other).isFalse();
        }

        // The item mode: as it is in a whole translation, the group's own in a group's.
        assertThat(XsltReader.ownName("item", "item", "event")).isTrue();
        assertThat(XsltReader.ownName(MODE + "_item", "item", "event")).isFalse();
        assertThat(XsltReader.ownName("pegaIntegrationItem", "item", MODE)).isTrue();
        assertThat(XsltReader.ownName("item", "item", MODE)).isFalse();
        assertThat(XsltReader.ownName(null, "item", MODE)).isFalse();
    }

    // --- helpers ----------------------------------------------------------------------------------------------------

    private static void assertReadBack(final Shape shape, final String naming, final boolean group) {
        final StringBuilder events = new StringBuilder("[");
        events.append(String.join(", ", shape.rules())).append(']');
        final Map<String, Object> json = json("""
                {"input": "json", "json_layout": "lines", "style": {"naming": "%s"},
                 "common": [{"path": "EventTime/TimeCreated", "field": "timestamp"},
                            {"path": "EventSource/System/Name", "value": "PEGA"},
                            {"path": "EventSource/System/Environment", "value": "Prod"},
                            {"path": "EventSource/Generator", "value": "Pega"},
                            {"path": "EventSource/Device/HostName", "field": "hostname"}],
                 "ignored_fields": ["app"],
                 "events": %s}""".formatted(naming, events));
        if (shape.forEach() != null) {
            json.put("for_each", shape.forEach());
        }
        final TranslationMapping whole = TranslationMapping.fromJson(json);
        final TranslationMapping mapping = group
                ? whole.withModule(module(List.of()))
                : whole;
        final String code = codeOf(mapping);

        final XsltReading reading = XsltReader.read(code);
        assertThat(reading.rules()).as("%s", reading.rules()).hasSize(shape.names().size())
                .allSatisfy(rule -> assertThat(rule.notes()).doesNotContain(NO_EVENT));

        final RebuiltMapping kept = XsltReader.rebuild(code, mapping, code);
        assertThat(kept.problems()).as("%s", kept.summary()).isEmpty();
        assertThat(kept.notes()).as("%s", kept.summary()).noneMatch(note -> note.contains(NO_EVENT));
        assertThat(kept.added()).as("%s", kept.summary()).isEmpty();
        assertThat(kept.removed()).as("%s", kept.summary()).isEmpty();
        assertThat(kept.reused()).as("%s", kept.summary()).isEqualTo(entries(mapping));
        assertThat(normalise(codeOf(kept.mapping()))).isEqualTo(normalise(code));

        final RebuiltMapping lost = XsltReader.rebuild(code, null, null);
        assertThat(lost.problems()).as("%s", lost.summary()).isEmpty();
        assertThat(lost.mapping().events()).extracting(EventRule::name)
                .containsExactlyElementsOf(shape.names());
        // A catch-all read back as marked, its log of the records it takes being the group's own helper's.
        assertThat(lost.mapping().events()).filteredOn(EventRule::catchAll).extracting(EventRule::name)
                .containsExactlyElementsOf(shape.names().contains("other")
                        ? List.of("other")
                        : List.of());
    }

    // Every entry a rule writes, the common ones in each rule that translates.
    private static int entries(final TranslationMapping mapping) {
        return mapping.events().stream()
                .filter(rule -> !rule.drop())
                .mapToInt(rule -> mapping.common().size() + rule.fields().size())
                .sum();
    }

    private static SourceTypeModule module(final List<SourceTypeModule.CommonPart> parts) {
        return new SourceTypeModule(SourceTypeModule.GROUP, "PEGA integration", MODE, List.of("PegaIntegration"),
                COMMON_HREF, null, parts, null, "sourcetype", List.of(new SourceTypeModule.Row("PegaIntegration",
                SourceTypeRows.VALUE, "PegaIntegration")));
    }

    private static TranslationMapping commonMapping() {
        return TranslationMapping.fromJson(json("""
                {"input": "json", "json_layout": "lines",
                 "common": [{"path": "EventTime/TimeCreated", "field": "timestamp"},
                            {"path": "EventSource/System/Name", "value": "PEGA"},
                            {"path": "EventSource/System/Environment", "value": "Prod"},
                            {"path": "EventSource/Device/HostName", "field": "hostname"}],
                 "events": [{"name": "common"}],
                 "module": {"kind": "common", "name": "PEGA", "mode": "pega_common",
                            "helpers": "%s"}}""".formatted(HELPERS_HREF)));
    }

    private static String codeOf(final TranslationMapping mapping) {
        final XsltGenerator.Generated result = XsltGenerator.generate(mapping, SCHEMA, "4.1.0");
        assertThat(result.ok()).as("%s", result.problems()).isTrue();
        return result.xslt();
    }

    // The XSLT without what isn't an edit (the declaration, space between tags).
    private static String normalise(final String text) {
        final String stripped = XsltVersionHistory.strip(text).replaceFirst("^\\s*<\\?xml[^>]*\\?>", "");
        return stripped.replaceAll(">\\s+<", "><").strip();
    }


    // --------------------------------------------------------------------------------


    // A mapping's rules (each a rule's JSON), its for_each (or null), and the rules' names read back lost.
    private record Shape(String forEach, List<String> rules, List<String> names) {

    }
}
