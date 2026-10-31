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

package stroom.ai.impl.tools.cef;

import stroom.ai.impl.tools.fielddoc.FieldDocFixtures;
import stroom.ai.impl.tools.fielddoc.FieldDocs;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.XmlElement;
import stroom.ai.impl.tools.generation.XsltStyle;
import stroom.ai.impl.tools.sample.ToolJson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The CEF output against its golden files for the same plans and Events: the drafted plans' JSON and notes, the
/// XSLTs' text, the lines' review, the documentation, the extension of a plan and what standing instructions say.
class TestCefGolden {

    @Test
    void theDraftIsItsGoldenJsonAndNotes() {
        final CefDraft.Drafted drafted = CefFixtures.drafted();
        final Map<String, Object> golden = FieldDocFixtures.goldenJson("cef/drafted.json");
        assertThat(ToolJson.dumps(drafted.plan().toJson(DumpMode.ALL))).isEqualTo(ToolJson.dumps(golden.get("plan")));
        assertThat(ToolJson.dumps(drafted.plan().toJson(DumpMode.COMPACT)))
                .isEqualTo(ToolJson.dumps(golden.get("compact")));
        assertThat(drafted.notes()).isEqualTo(golden.get("notes"));
        assertThat(drafted.plan().problems()).isEqualTo(golden.get("problems"));
    }

    @Test
    void aKeptPlanReadsBackTheSame() {
        final Map<String, Object> golden = FieldDocFixtures.goldenJson("cef/drafted.json");
        @SuppressWarnings("unchecked") final Map<String, Object> compact = (Map<String, Object>) golden.get("compact");
        final CefPlan plan = CefPlan.fromJson(compact);
        assertThat(plan).isEqualTo(CefFixtures.drafted().plan());
        assertThat(ToolJson.dumps(plan.toJson(DumpMode.ALL))).isEqualTo(ToolJson.dumps(golden.get("plan")));
    }

    @Test
    void theKafkaXsltIsItsGolden() {
        assertThat(CefFixtures.drafted().plan().xslt()).isEqualTo(FieldDocFixtures.golden("cef/drafted-kafka.xslt"));
    }

    @Test
    void theTextXsltIsItsGolden() {
        assertThat(CefFixtures.drafted(false, List.of(), CefPlan.TEXT).plan().xslt())
                .isEqualTo(FieldDocFixtures.golden("cef/drafted-text.xslt"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"named", "inline", "top"})
    void eachStylesXsltIsItsGolden(final String name) {
        final Map<String, Object> style = switch (name) {
            case "named" -> Map.of("layout", "named", "naming", "camelCase");
            case "inline" -> Map.of("layout", "inline");
            default -> Map.of("variables", "top");
        };
        final CefPlan text = CefFixtures.drafted().plan().withOutput(CefPlan.TEXT);
        assertThat(text.withStyle(XsltStyle.fromJson(style)).xslt())
                .isEqualTo(FieldDocFixtures.golden("cef/style-" + name + ".xslt"));
    }

    @Test
    void aMappedSeveritysXsltIsItsGolden() {
        final Map<String, String> severities = new LinkedHashMap<>();
        severities.put("low", "3");
        severities.put("medium", "6");
        severities.put("high", "9");
        final CefPlan mapped = CefFixtures.drafted().plan().withOutput(CefPlan.TEXT)
                .withHeader("severity", new CefValue(null, "EventDetail/Alert/Severity", severities, "3"));
        assertThat(mapped.xslt()).isEqualTo(FieldDocFixtures.golden("cef/severity-map.xslt"));
    }

    @ParameterizedTest
    @ValueSource(strings = {CefPlan.KAFKA, CefPlan.TEXT})
    void importsAndAnEscapedTopicAreTheGolden(final String output) {
        final Map<String, CefValue> header = new LinkedHashMap<>();
        header.put("vendor", CefValue.constant("Ac'me"));
        header.put("product", CefValue.constant("P"));
        header.put("version", CefValue.constant("1"));
        header.put("signature", new CefValue(null, "EventDetail/TypeId", Map.of(), "x"));
        header.put("name", CefValue.constant("n"));
        final CefPlan plan = CefPlan.of(output, "t{x}", false, header,
                        List.of(CefField.of("EventSource/User/Id", "suser")), Map.of())
                .withKafkaKey("EventSource/User/Id")
                .withImports(List.of("Cef-Common-V1", "Cef-Common-V1", "A&B"));
        assertThat(plan.xslt()).isEqualTo(FieldDocFixtures.golden("cef/imports-" + output + ".xslt"));
    }

    @Test
    void theLinesTheirExamplesAndReviewAreTheGoldens() {
        final CefPlan plan = CefFixtures.drafted().plan();
        final Map<String, Object> golden = FieldDocFixtures.goldenJson("cef/drafted-lines.json");
        final List<String> lines = CefLines.linesIn(CefFixtures.run(plan.xslt(), CefFixtures.EVENTS));
        assertThat(lines).isEqualTo(golden.get("lines"));
        final Map<CefDocs.KindKey, String> examples = CefLines.examplesFrom(plan, lines, CefFixtures.events());
        final List<List<String>> said = new ArrayList<>();
        examples.forEach((k, v) -> said.add(List.of(k.kind(), k.key(), v)));
        assertThat(said).isEqualTo(golden.get("examples"));
        assertThat(ToolJson.dumps(CefLines.review(lines, CefFixtures.events(), false)))
                .isEqualTo(ToolJson.dumps(golden.get("review")));
    }

    @Test
    void theDocumentationIsItsGolden() {
        final CefPlan plan = CefFixtures.drafted().plan();
        final Map<CefDocs.KindKey, String> examples = goldenExamples();
        assertThat(plan.markdown(examples, FieldDocs.BY_EVENT_TYPE, null))
                .isEqualTo(FieldDocFixtures.golden("cef/drafted.md"));
        assertThat(plan.markdown(examples, FieldDocs.COMPACT, null))
                .isEqualTo(FieldDocFixtures.golden("cef/drafted-compact.md"));
    }

    @Test
    void anOverriddenDraftIsItsGolden() {
        final CefDraft.Drafted drafted = CefFixtures.drafted(false, FieldDocFixtures.json("""
                [{"path": "EventDetail/Authenticate/User/Id", "key": "deviceCustomString6", "label": "Target user"},
                 {"path": "EventDetail/Authenticate/Data[@Name='note']/@Value", "drop": true},
                 {"path": "EventSource/Nothing", "key": "cs2"}]"""), CefPlan.KAFKA);
        final Map<String, Object> golden = FieldDocFixtures.goldenJson("cef/overridden.json");
        assertThat(ToolJson.dumps(drafted.plan().toJson(DumpMode.ALL))).isEqualTo(ToolJson.dumps(golden.get("plan")));
        assertThat(drafted.notes()).isEqualTo(golden.get("notes"));
    }

    @Test
    void draftsPastTheSlotsAreTheGoldens() {
        final StringBuilder many = new StringBuilder();
        for (int n = 0; n < 12; n++) {
            many.append("<Data Name=\"d").append(n).append("\" Value=\"value ").append(n).append("\"/>");
        }
        final List<XmlElement> events = CefEvents.eventsOf(CefFixtures.EVENTS.replace("<Data Name=\"session_id\"",
                many + "<Data Name=\"session_id\""));
        final CefDraft.Drafted closed = CefDraft.draft(events, false, List.of(), null, CefPlan.KAFKA, "t", null);
        final CefDraft.Drafted opened = CefDraft.draft(events, true, List.of(), null, CefPlan.KAFKA, "t", null);
        final Map<String, Object> golden = FieldDocFixtures.goldenJson("cef/many.json");
        assertThat(ToolJson.dumps(closed.plan().toJson(DumpMode.ALL))).isEqualTo(ToolJson.dumps(golden.get("closed")));
        assertThat(closed.notes()).isEqualTo(golden.get("closed_notes"));
        assertThat(ToolJson.dumps(opened.plan().toJson(DumpMode.ALL))).isEqualTo(ToolJson.dumps(golden.get("opened")));
        assertThat(opened.notes()).isEqualTo(golden.get("opened_notes"));
        assertThat(opened.plan().markdown(Map.of(), FieldDocs.BY_EVENT_TYPE, null))
                .isEqualTo(FieldDocFixtures.golden("cef/many-open.md"));
    }

    @Test
    void reviewsAndParsesAreTheGoldens() {
        final List<String> lines = List.of(
                "CEF:0|Delinea|SecretServer|11.4|User-Login|User logged in|3|src=not-an-ip cs1=Prod myField=x "
                + "suser=priya.patel rt=1790552890000 cs2Label=x cn1=abc rt=12 end=yesterday",
                "CEF:0|Delinea|SecretServer|only five|3|x=1",
                "CEF:0|D|S|1|Secret-View|Secret viewed: a=b|c|3|suser=b",
                "CEF:0|D|S|1|X|Y|99|act=" + "a".repeat(70) + " dmac=zz",
                "CEF:0|D|S|1|X|Y|3|msg=first\nsecond");
        final Map<String, Object> golden = FieldDocFixtures.goldenJson("cef/review.json");
        assertThat(ToolJson.dumps(CefLines.review(lines, CefFixtures.events().subList(0, 1), false)))
                .isEqualTo(ToolJson.dumps(golden.get("review")));
        assertThat(ToolJson.dumps(CefLines.review(lines, List.of(), null)))
                .isEqualTo(ToolJson.dumps(golden.get("review_none")));
        assertThat(ToolJson.dumps(lines.stream().map(line -> CefLines.parse(line).toMap()).toList()))
                .isEqualTo(ToolJson.dumps(golden.get("parsed")));
    }

    @Test
    void anExtendedPlanIsItsGolden() {
        final Map<String, CefValue> header = new LinkedHashMap<>();
        header.put("vendor", CefValue.constant("Acme"));
        header.put("product", CefValue.constant("P"));
        header.put("version", CefValue.constant("1"));
        header.put("signature", CefValue.from("EventDetail/TypeId"));
        header.put("name", CefValue.constant("n"));
        final CefPlan plan = CefPlan.of(CefPlan.KAFKA, "t", false, header,
                List.of(CefField.of("EventSource/User/Id", "suser")),
                Map.of("View", List.of(new CefField("EventDetail/View/Document/Title", "cs1", "Title", null))));
        final Map<String, Object> golden = FieldDocFixtures.goldenJson("cef/extend.json");
        final CefDraft.Extended few = CefDraft.extend(plan, CefEvents.eventsOf(TestCefPlan.COVERAGE_EVENTS));
        assertThat(ToolJson.dumps(few.added())).isEqualTo(ToolJson.dumps(golden.get("added")));
        assertThat(few.notes()).isEqualTo(golden.get("notes"));
        final CefDraft.Extended all = CefDraft.extend(plan, CefFixtures.events());
        assertThat(ToolJson.dumps(all.added())).isEqualTo(ToolJson.dumps(golden.get("added_all")));
        assertThat(all.notes()).isEqualTo(golden.get("notes_all"));
    }

    @Test
    void whatTheInstructionsSayIsTheGolden() {
        final Map<String, Object> said = CefInstructions.fromInstructions(List.of(
                "## CEF output\nCustom CEF keys are not allowed.\nKafka topic: arcsight-secretserver\n"
                + "- EventDetail/Authorise/User/Name -> deviceCustomString6 (label: 'Authorised user')\n"
                + "- EventSource/System/Environment -> cs1\nPipeline template: CEF to ArcSight\n"
                + "Device vendor: Delinea\n",
                "Custom keys are allowed here.\n- EventDetail/View/Resource/Name maps to `myKey` as “The name”"));
        final Map<String, Object> out = new LinkedHashMap<>(said);
        @SuppressWarnings("unchecked") final List<CefOverride> overrides = (List<CefOverride>) out.remove(
                CefInstructions.OVERRIDES);
        out.put(CefInstructions.OVERRIDES, overrides.stream().map(o -> o.toJson(DumpMode.ALL)).toList());
        assertThat(ToolJson.dumps(out)).isEqualTo(ToolJson.dumps(FieldDocFixtures.goldenJson("cef/instructions.json")));
    }

    @Test
    void sendAndReceiveDocumentedTogetherIsTheGolden() {
        final Map<String, Object> golden = FieldDocFixtures.goldenJson("cef/send-receive.json");
        @SuppressWarnings("unchecked") final CefPlan plan = CefPlan.fromJson((Map<String, Object>) golden.get("plan"));
        assertThat(ToolJson.dumps(plan.toJson(DumpMode.COMPACT))).isEqualTo(ToolJson.dumps(golden.get("compact")));
        final Map<CefDocs.KindKey, String> examples = new LinkedHashMap<>();
        examples.put(new CefDocs.KindKey("Send", "suser"), "alice");
        examples.put(new CefDocs.KindKey("Receive", "suser"), "bob");
        examples.put(new CefDocs.KindKey("Send", "duser"), "carol");
        examples.put(new CefDocs.KindKey("Receive", "duser"), "carol");
        assertThat(plan.markdown(examples, FieldDocs.BY_EVENT_TYPE, null))
                .isEqualTo(FieldDocFixtures.golden("cef/send-receive.md"));
    }

    @SuppressWarnings("unchecked")
    private static Map<CefDocs.KindKey, String> goldenExamples() {
        final Map<String, Object> golden = FieldDocFixtures.goldenJson("cef/drafted-lines.json");
        final Map<CefDocs.KindKey, String> examples = new LinkedHashMap<>();
        for (final Object item : (List<Object>) golden.get("examples")) {
            final List<String> example = (List<String>) item;
            examples.put(new CefDocs.KindKey(example.get(0), example.get(1)), example.get(2));
        }
        return examples;
    }
}
