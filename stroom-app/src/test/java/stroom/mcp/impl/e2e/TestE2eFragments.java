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

package stroom.mcp.impl.e2e;

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.MergedPipeline;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/// An XML fragments source end to end through the REAL tools on a real Stroom, as seen in a test environment. The
/// source sends `<Event>` elements, one per line with no root, whose `EventData/Data` holds a message with en dashes
/// between its parts ("Sep 27 2026 23:48:10 – User: domain.com\Bloggs, Joe – [[AppServer]] Event: [User] Action:
/// [Login] ...").
///
/// Covered: 1 (profile_sample names XML fragments and the XMLFragmentParser's wrapper converter), 2 (a child of Event
/// Data (XML) with its parser replaced: Source stored, feeding the XMLFragmentParser, which has the build's wrapper), 3
/// (a regex with '-' where the text has an en dash refused at once, saying where and which character; fixed, it saves,
/// and every record steps clean, valid, with its user and kind) and 4 (build_translation_xslt steps the pipeline only
/// when the field mapping preview is asked for).
@SuppressWarnings("unchecked")
class TestE2eFragments extends AbstractE2eTest {

    private static final String BUILD = "frag";
    private static final String FEED = "FRAG";
    private static final List<String> ACTIONS = List.of("Login", "Logout", "Login", "Update");
    static final String SAMPLE = IntStream.range(0, ACTIONS.size())
            .mapToObj(n -> "<Event><System><Provider Name=\"AppAudit\"/><EventID>" + (219 + n) + "</EventID>"
                           + "<Computer>app01.domain.com</Computer></System><EventData><Data>Sep 27 2026 23:48:"
                           + (10 + n) + " – User: domain.com\\Bloggs, Joe – [[AppServer]] Event: [User] Action: ["
                           + ACTIONS.get(n) + "] By User: domain.com\\joe.bloggs (Item Id: " + (219 + n) + ")"
                           + "</Data></EventData></Event>")
            .collect(Collectors.joining("\n")) + "\n";
    private static final String DASHED = "^(\\S+ \\d+ \\d+ \\S+) - User: (.+?) - \\[\\[(\\w+)\\]\\] "
                                         + "Event: \\[(\\w+)\\] Action: \\[(\\w+)\\] "
                                         + "By User: (\\S+) \\(Item Id: (\\d+)\\)$";
    private static final Pattern EVENT_USER = Pattern.compile(
            "<EventSource>.*?<User>\\s*<Id>([^<]*)</Id>", Pattern.DOTALL);
    private static final Pattern TYPE_ID = Pattern.compile("<TypeId>([^<]*)</TypeId>");

    @Inject
    private DocumentAccess documentAccess;

    @Test
    void anXmlFragmentsSourceIsOnboardedEndToEnd() {
        // 1. The sample: XML fragments, and what reads them.
        final Map<String, Object> profile = json("profile_sample", args("sample", SAMPLE));
        dump("profile_sample", profile);
        assertThat(profile).containsEntry("format", "xml fragments");
        assertThat(map(profile, "text_converter")).containsEntry("type", "XML_FRAGMENT");
        createFeed(BUILD, FEED);
        final long raw = upload(FEED, SAMPLE);
        // The wrapper's namespace, as profile_sample gives it: the environment's own wrapper may differ.
        final String namespace = (String) map(profile, "xslt_input").get("namespace");
        final Map<String, Object> converter = json("save_text_converter", args("build", BUILD, "name", FEED,
                "converter_type", "XML_FRAGMENT", "code", map(profile, "text_converter").get("code")));
        final String converterUuid = (String) converter.get("uuid");

        // 3. The regex, tried on the sample (before the pipeline, as an agent drafting would).
        Instant started = Instant.now();
        final Map<String, Object> refused = json("build_translation_xslt", args("mapping",
                mapping(DASHED, namespace), "schema_version", VERSION, "stream_ids",
                List.of(raw)));
        Duration took = Duration.between(started, Instant.now());
        assertThat(took).isLessThan(Duration.ofSeconds(15));
        dump("build_translation_xslt dashed", refused);
        assertThat(refused).containsEntry("ok", false);
        final String problem = ((List<String>) refused.get("problems")).stream()
                .filter(text -> text.contains("extract[0]"))
                .findFirst()
                .orElse("");
        assertThat(problem).contains("matches none of the 4 sample texts", "EN DASH (U+2013)");
        final String fixed = DASHED.replace(" - ", " – ");
        final Map<String, Object> saved = json("build_translation_xslt", args("mapping", mapping(fixed, namespace),
                "schema_version", VERSION, "stream_ids", List.of(raw), "build", BUILD,
                "name", FEED + "-Events"));
        assertThat(saved).as("saved: %s", saved.get("problems")).containsEntry("ok", true);
        assertThat(saved.get("saved")).isNotNull();
        // Next, as the plan has it: the template to make the pipeline from.
        assertThat(nextTool(saved)).isEqualTo("find_pipeline_templates");

        // 2. The pipeline: Event Data (XML), its parser replaced.
        final Map<String, Object> pipeline = json("create_pipeline", args("build", BUILD, "name", FEED + "-Events",
                "template_uuid", templateUuid("translation", "Event Data (XML)"),
                "replace_parser", "XMLFragmentParser"));
        dump("create_pipeline", pipeline);
        final String pipelineUuid = (String) pipeline.get("uuid");
        final MergedPipeline merged = asUser(administrator(), () -> MergedPipeline.load(documentAccess,
                pipelineUuid)).orElseThrow();
        final Map<String, String> types = merged.elements();
        final List<String> chain = merged.chain();
        // The template stores no Source; the child does, linked to the new parser, as the UI draws it (seen:
        // Source shown linked to nothing, though the pipeline processed).
        // (MergedPipeline leaves out the Source element Stroom always adds: the child's own layer is read for it.)
        assertThat(added(pipelineUuid, "elements")).as("Source is stored in the child")
                .anySatisfy(element -> assertThat(element)
                        .containsEntry("id", "Source")
                        .containsEntry("type", "Source"));
        assertThat(added(pipelineUuid, "links")).as("Source feeds the XMLFragmentParser")
                .anySatisfy(link -> assertThat(link)
                        .containsEntry("from", "Source")
                        .containsEntry("to", "xmlFragmentParser"));
        assertThat(chain.getFirst()).as("the XMLFragmentParser starts the chain: %s", chain)
                .isEqualTo("xmlFragmentParser");
        assertThat(types.values()).contains("XMLFragmentParser").doesNotContain("XMLParser");
        assertThat(types).doesNotContainKey("xmlParser");
        final Object wrapper = merged.properties().get(new MergedPipeline.ElementProperty("xmlFragmentParser",
                "textConverter"));
        assertThat(String.valueOf(wrapper)).as("the build's wrapper converter set on it").contains(converterUuid);
        assertThat(nextTool(pipeline)).isEqualTo("step_sample");

        // 3. Stepped clean, with the user and action from every record.
        final Map<String, Object> sample = json("step_sample", args("pipeline_uuid", pipelineUuid,
                "stream_ids", List.of(raw)));
        assertThat(sample).as("stepped clean: %s", sample.get("groups")).containsEntry("verdict", "clean");
        final List<String> users = new ArrayList<>();
        final List<String> kinds = new ArrayList<>();
        for (int record = 0; record < number(sample.get("records_stepped")); record++) {
            final String output = translationOutput(pipelineUuid, raw, record, 0);
            final Map<String, Object> valid = json("check_events", args("events_xml", output));
            assertThat(map(valid, "schema")).as("record %s valid: %s", record, valid)
                    .containsEntry("valid", true);
            final Matcher user = EVENT_USER.matcher(output);
            final Matcher kind = TYPE_ID.matcher(output);
            assertThat(user.find()).as("record %s gave an Event: %s", record, output).isTrue();
            users.add(user.group(1));
            kinds.add(kind.find()
                    ? kind.group(1)
                    : null);
        }
        assertThat(users).containsExactly("joe.bloggs", "joe.bloggs", "joe.bloggs", "joe.bloggs");
        assertThat(kinds).containsExactly("Logon", "Logoff", "Logon", "Update");

        // 4. Quick while iterating: the pipeline is stepped only when the preview is asked for.
        started = Instant.now();
        final Map<String, Object> again = json("build_translation_xslt", args("mapping", mapping(fixed, namespace),
                "schema_version", VERSION, "stream_ids", List.of(raw),
                "pipeline_uuid", pipelineUuid));
        took = Duration.between(started, Instant.now());
        assertThat(again).containsEntry("ok", true);
        assertThat(again.get("field_mapping")).as("not stepped").isNull();
        assertThat(took).isLessThan(Duration.ofSeconds(15));
        final Map<String, Object> preview = json("build_translation_xslt", args("mapping", mapping(fixed, namespace),
                "schema_version", VERSION, "stream_ids", List.of(raw),
                "pipeline_uuid", pipelineUuid, "field_mapping", true));
        assertThat(preview.get("field_mapping")).as("asked for, the preview steps the sample").isNotNull();
        assertThat(number(map(preview, "field_mapping_sample").get("records"))).isEqualTo(4);
    }

    // The mapping: the message's parts extracted with the regex, a rule per action.
    private static Map<String, Object> mapping(final String regex, final String namespace) {
        final Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("input", "xml_fragments");
        mapping.put("record", "Event");
        mapping.put("xml_namespace", namespace);
        mapping.put("extract", List.of(Map.of("xpath", "EventData/Data", "regex", regex,
                "names", List.of("when", "subject", "server", "kind", "action", "user", "item"))));
        mapping.put("common", List.of(
                Map.of("path", "EventTime/TimeCreated", "field", "when", "time_format", "MMM dd yyyy HH:mm:ss"),
                Map.of("path", "EventSource/System/Name", "value", "AppAudit"),
                Map.of("path", "EventSource/System/Environment", "value", "Test"),
                Map.of("path", "EventSource/Generator", "xpath", "System/Provider/@Name"),
                Map.of("path", "EventSource/Device/HostName", "xpath", "System/Computer"),
                Map.of("path", "EventSource/User/Id", "field", "user", "transform", "strip_domain")));
        mapping.put("events", List.of(
                Map.of("name", "logon", "when", List.of(Map.of("field", "action", "equals", "Login")),
                        "fields", List.of(
                                Map.of("path", "EventDetail/TypeId", "value", "Logon"),
                                Map.of("path", "EventDetail/Authenticate/Action", "value", "Logon"),
                                Map.of("path", "EventDetail/Authenticate/User/Id", "field", "user",
                                        "transform", "strip_domain"),
                                Map.of("path", "EventDetail/Authenticate/Data", "data_name", "server",
                                        "field", "server"))),
                Map.of("name", "logoff", "when", List.of(Map.of("field", "action", "equals", "Logout")),
                        "fields", List.of(
                                Map.of("path", "EventDetail/TypeId", "value", "Logoff"),
                                Map.of("path", "EventDetail/Authenticate/Action", "value", "Logoff"),
                                Map.of("path", "EventDetail/Authenticate/User/Id", "field", "user",
                                        "transform", "strip_domain"))),
                Map.of("name", "update", "when", List.of(Map.of("field", "action", "equals", "Update")),
                        "fields", List.of(
                                Map.of("path", "EventDetail/TypeId", "value", "Update"),
                                Map.of("path", "EventDetail/Update/After/Object/Id", "field", "item"),
                                Map.of("path", "EventDetail/Update/Data", "data_name", "subject",
                                        "field", "subject")))));
        return mapping;
    }
}
