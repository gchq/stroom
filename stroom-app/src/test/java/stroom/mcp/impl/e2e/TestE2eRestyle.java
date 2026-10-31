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

import stroom.ai.impl.tools.KeptMapping;
import stroom.mcp.impl.McpTestClient;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/// An events pipeline in a build brought up to the generator's current style, as asked of an agent in production,
/// driving the real tools. A FortiGate-like source: JSON records whose body is key="value" pairs, in three streams, the
/// VPN records only in the last. Every numbered part is covered:
///
/// 1. The mapping saved over all three streams finds the VPN rule's keys; over the first alone, a key it lacks is
///    named as such.
/// 2. An earlier generator simulated: build_status says it was written by an earlier generator and is unchanged,
///    not edited by hand, and names the call that regenerates it.
/// 3. The XSLT's kept mapping summarised (read_document), whole on asking.
/// 4. Regenerated with uuid alone: the current style, smaller, clean, the same Events. 4b. A hand edit named by
///    build_status and carried into the mapping by rebuild_mapping. 4c. The Documentation tab cleared: the loss
///    named, and the mapping rebuilt from the XSLT alone. 4d. An edit no mapping can express: refused with the
///    differences, saved once accepted.
/// 5. A hand edit calling a function Stroom hasn't got: called a hand edit, blocking when stepped, regenerating over
///    it refused until the user says to drop it.
/// 6. A function call given as a field is refused, with the extract list named.
@SuppressWarnings("unchecked")
class TestE2eRestyle extends AbstractE2eTest {

    // Where read_document says a kept mapping too large to show was saved.
    private static final Pattern SAVED_TO = Pattern.compile("^It is too large to show .*?, so it has been saved to "
                                                            + "(\\S+): ");

    private static final String BUILD = "restyle";
    private static final String FEED = "FW-JSON";
    private static final String PIPELINE = FEED + "-Events";
    private static final List<String> ACTIONS = List.of("deny", "accept", "close");
    private static final List<String> QUOTED = List.of("logid", "subtype", "level", "vd", "srcintf", "srcintfrole",
            "dstintf", "dstintfrole", "action", "service", "logdesc", "outintf", "vpntunnel", "status");
    private static final List<String> BARE = List.of("srcip", "srcport", "dstip", "dstport", "policyid", "proto",
            "remip", "locip", "remport", "locport");
    private static final String RULE_CALL = "<xsl:apply-templates select=\".\" mode=\"rule\"/>";
    private static final String XSL = "http://www.w3.org/1999/XSL/Transform";

    private List<Long> raws;
    private String xsltUuid;
    private String pipelineUuid;
    private List<Checked> checked;

    @Override
    boolean followsNext() {
        return true;
    }

    @Test
    void anEventsPipelineBroughtUpToTheCurrentStyle() {
        final Map<String, Object> mapping = mapping();
        final String oldCode = feedAndOldStyle(mapping);
        final List<String> before = earlierGenerator(oldCode);
        describedMapping();
        regenerated(oldCode, before);
        final List<String> byHand = handEdit();
        cleared(byHand);
        inexpressible();
        missingFunction();
        functionAsField(mapping);
    }

    // ### 1. a FortiGate-like feed, its VPN records past the 300th; the mapping checked against every record
    private String feedAndOldStyle(final Map<String, Object> mapping) {
        createFeed(BUILD, FEED);
        final List<List<Map<String, Object>>> streams = streams();
        raws = new ArrayList<>();
        for (final List<Map<String, Object>> records : streams) {
            raws.add(upload(FEED, JsonUtil.writeValueAsString(records, false)));
        }
        checked = List.of(new Checked(raws.get(0), IntStream.range(0, 6).boxed().toList()),
                new Checked(raws.get(2), IntStream.range(44, 56).boxed().toList()));
        final Map<String, Object> firstOnly = json("build_translation_xslt", args("mapping", mapping, "stream_ids",
                List.of(raws.getFirst()), "schema_version", VERSION));
        final List<String> lacking = strings(firstOnly, "problems").stream()
                .filter(problem -> problem.contains("['vpntunnel']"))
                .toList();
        assertThat(lacking).as("the first stream alone lacks the VPN keys: %s", firstOnly.get("problems"))
                .hasSize(1);
        assertThat(lacking.getFirst()).as("and says so").contains("None of these texts has `vpntunnel=\"`",
                "its records may be elsewhere");
        final Map<String, Object> oldStyle = new LinkedHashMap<>(mapping);
        oldStyle.put("style", Map.of("variables", "top", "data_values", "attribute", "data_entries", "guarded",
                "data_run_min", 99));
        final Map<String, Object> built = agreed("build_translation_xslt", args("mapping", oldStyle, "stream_ids",
                raws, "build", BUILD, "name", PIPELINE, "include_xslt", false, "schema_version", VERSION));
        assertThat(built.get("ok")).as("over all %s records: %s", streams.stream().mapToInt(List::size).sum(),
                built.get("problems")).isEqualTo(true);
        assertThat(strings(built, "problems")).as("the VPN keys are found").noneMatch(p -> p.contains("vpntunnel"));
        xsltUuid = (String) map(built, "saved").get("uuid");
        pipelineUuid = (String) createPipeline(BUILD, PIPELINE, templateUuid("translation", "Event Data (JSON)"),
                valueProperty("jsonParser", "addRootObject", false),
                docProperty("translationFilter", "xslt", xsltUuid, "XSLT")).get("uuid");
        return readXslt(xsltUuid).getData();
    }

    // ### 2. as an earlier generator left it: build_status names a generator upgrade, not a hand edit
    private List<String> earlierGenerator(final String oldCode) {
        editXslt(xsltUuid, doc -> {
            final KeptMapping.Kept kept = KeptMapping.readMapping(doc.getDescription()).orElseThrow();
            final Map<String, Object> payload = new LinkedHashMap<>(kept.payload());
            final Map<String, Object> keptMapping = new LinkedHashMap<>((Map<String, Object>) payload.get("mapping"));
            // An earlier server kept no style: those were its defaults.
            keptMapping.remove("style");
            payload.put("mapping", keptMapping);
            return doc.copy().description(KeptMapping.withMapping(doc.getDescription(), kept.kind(), payload))
                    .build();
        });
        final String drift = driftOf();
        assertThat(drift).as("build_status: %s", drift).contains("written by an earlier generator",
                "unchanged since", "uuid='" + xsltUuid + "' (no mapping) regenerates it").doesNotContain("by hand");
        final Map<String, Object> stepped = stepSample(pipelineUuid, raws);
        assertThat(stepped.get("verdict")).as("the old XSLT steps clean: %s", said(list(stepped, "groups")))
                .isEqualTo("clean");
        final List<String> before = events();
        assertThat(before).as("traffic, and the VPN records at the end").hasSize(18);
        assertThat(before.stream().filter(event -> event.contains("VpnConnect"))).as("6 of them VPN").hasSize(6);
        return before;
    }

    // ### 3. describe_document summarises the kept mapping instead of returning it
    private void describedMapping() {
        final String described = readDocument(false);
        final Map<String, Object> summary = keptSection(described);
        assertThat(summary).as("summary").containsEntry("kind", "translation")
                .containsEntry("extractions", 24)
                .containsEntry("style", "the generator's current defaults");
        assertThat(strings(summary, "rules")).as("its rules").hasSize(4);
        assertThat(described.substring(0, described.indexOf("## Kept mapping"))).as("the description shown "
                                                                                     + "without the mapping")
                .doesNotContain("\"extract\"");
        assertThat((String) summary.get("how")).as("how to change it").contains("uuid='" + xsltUuid
                                                                                 + "' alone regenerates it");
        // mapping=true gives it whole, to read: too large to show, so saved to the workspace, read with jq.
        final String whole = readDocument(true);
        final Matcher saved = SAVED_TO.matcher(whole.substring(whole.indexOf("## Kept mapping\n")
                                                               + "## Kept mapping\n".length()));
        assertThat(saved.find()).as("the whole mapping saved to the workspace: %s", whole).isTrue();
        assertThat(McpTestClient.text(client.call("jq", args("path", saved.group(1), "query",
                ".mapping.extract | length")))).as("its extractions, read back").isEqualTo("24");
    }

    // ### 4. regenerated with uuid alone: the current style, the same Events
    private void regenerated(final String oldCode, final List<String> before) {
        final Map<String, Object> regenerated = agreed("build_translation_xslt", args("uuid", xsltUuid,
                "stream_ids", raws, "include_xslt", false));
        assertThat(regenerated.get("ok")).as("regenerated: %s", regenerated.get("problems")).isEqualTo(true);
        assertThat(regenerated.get("saved")).as("and saved").isNotNull();
        final String newCode = readXslt(xsltUuid).getData();
        assertThat(newCode).as("one function a key=value shape, called with the key")
                .contains("<xsl:function name=\"mcp:quoted_value\"", "mcp:quoted_value($body, 'dstintfrole')",
                        "<xsl:function name=\"mcp:value\"");
        assertThat(newCode).as("the Network rules' Source and Destination written once, whatever their action "
                               + "element").contains("mode=\"source\"", "mode=\"destination\"");
        assertThat(newCode).as("Data entries one line each, through mcp:data")
                .contains("<xsl:sequence select=\"mcp:data('dstintfrole', mcp:quoted_value($body, "
                          + "'dstintfrole'))\"/>")
                .doesNotContain("<xsl:attribute name=\"Value\"", "<Data Name=\"dstintfrole\"");
        final Set<String> runs = new TreeSet<>();
        final Matcher matcher = Pattern.compile("mode=\"(data_[^\"]+)\"").matcher(newCode);
        while (matcher.find()) {
            runs.add(matcher.group(1));
        }
        assertThat(runs).as("the Data runs the Network rules share").isNotEmpty()
                .allMatch(run -> occurrences(newCode, "mode=\"" + run + "\"/>") >= 2);
        assertThat(justInTime(oldCode)).as("the old code declares its variables at each template's start")
                .isFalse();
        assertThat(justInTime(newCode)).as("the new code where first used").isTrue();
        assertThat(newCode.length()).as("smaller").isLessThan(oldCode.length());
        final String described = readDocument(false);
        assertThat(pendingChanges(described)).as("pending changes").last()
                .isEqualTo("Regenerated from its mapping");
        final List<String> preview = readXslt(xsltUuid).getDescription().lines()
                .map(String::strip)
                .filter(line -> line.startsWith("| Unreleased |"))
                .toList();
        assertThat(preview).as("the build's changes previewed in its Documentation, Unreleased").hasSize(1);
        assertThat(preview.getFirst()).contains("Created from its mapping; Regenerated from its mapping");
        assertThat(newCode).as("the code left alone").doesNotContain("version history");
        final Map<String, Object> stepped = stepSample(pipelineUuid, raws);
        assertThat(stepped.get("verdict")).as("steps clean: %s", said(list(stepped, "groups"))).isEqualTo("clean");
        // Seen in VS Code: promotion said no clean step of the regenerated code was recorded, though it had stepped
        // clean.
        final Map<String, Object> pipeline = list(json("build_status", args("build", BUILD)), "pipelines").stream()
                .filter(doc -> pipelineUuid.equals(doc.get("uuid")))
                .findFirst()
                .orElseThrow();
        assertThat(pipeline.get("stepped")).as("the clean step of the regenerated code is recorded, as promotion "
                                               + "checks it").isNotIn(null, false, "");
        assertThat(events()).as("every Event the same as the old XSLT wrote, the VPN ones included")
                .isEqualTo(before);
        assertThat(driftOf()).as("build_status: nothing to say about the XSLT").isEmpty();
    }

    // ### 4b. a hand edit: a new Data element, and the Rule no longer written; carried into the mapping
    private List<String> handEdit() {
        // Asked for by the user: someone changes the XSLT in Stroom's editor, to write a new field or to stop
        // writing one.
        final String code = readXslt(xsltUuid).getData();
        final int deny = code.indexOf("mode=\"event_type_traffic_deny\"");
        final int end = code.indexOf("</Deny>", deny);
        String body = code.substring(deny, end);
        assertThat(body).as("the Deny rule writes its Rule through the shared template").contains(RULE_CALL);
        body = body.replaceFirst(Pattern.quote(RULE_CALL), "")
               + "<Data Name=\"collector\" Value=\"{*[@key='hostname']}\"/>\n          ";
        final String edited = code.substring(0, deny) + body + code.substring(end);
        editXslt(xsltUuid, doc -> doc.copy().data(edited).build());
        final String drift = driftOf();
        assertThat(drift).as("build_status names both changes: %s", drift)
                .contains("edited by hand since the server saved it", "+ <Data Name=\"collector\"",
                        "- " + RULE_CALL);
        assertThat(stepSample(pipelineUuid, raws).get("verdict")).as("the hand edit steps clean")
                .isEqualTo("clean");
        final List<String> byHand = events();
        final List<String> denied = byHand.stream()
                .filter(event -> event.contains("<TypeId>traffic-deny</TypeId>"))
                .toList();
        assertThat(denied).as("its Deny events: the collector written, no Rule").isNotEmpty()
                .allMatch(event -> event.contains("Name=\"collector\"") && !event.contains("<Rule>"));
        assertThat(drift).as("build_status names rebuild_mapping to carry it in").contains("rebuild_mapping");
        final Map<String, Object> redone = agreed("rebuild_mapping", args("uuid", xsltUuid, "stream_ids", raws,
                "change", "Collector for denied traffic, no rule id: a hand edit carried in"));
        dump("rebuild_mapping", redone);
        final Map<String, Object> summary = map(redone, "rebuilt");
        assertThat(redone.get("saved")).as("rebuild_mapping saved it").isNotNull();
        assertThat(strings(summary, "entries_new")).as("the edit carried in")
                .containsExactly("rule traffic-deny: EventDetail/Network/Deny/Data Data collector");
        assertThat(strings(summary, "entries_removed")).as("and the Rule removed")
                .containsExactly("rule traffic-deny: EventDetail/Network/Deny/Rule");
        assertThat(((Number) summary.get("entries_kept")).intValue()).as("entries kept as they were")
                .isGreaterThan(50);
        assertThat(summary).doesNotContainKey("kept_as_xpath");
        assertThat(map(redone, "proven_on")).as("proven on 200 records stepped in Stroom")
                .containsEntry("records", 200);
        assertThat(redone).as("the same output").doesNotContainKey("differences");
        final Map<String, Object> denyRule = E2eFixtures.rule(keptMapping(xsltUuid), "traffic-deny");
        assertThat(E2eFixtures.entries(denyRule)).as("the kept mapping has the collector, read as a field")
                .contains(Map.of("path", "EventDetail/Network/Deny/Data", "data_name", "collector", "field",
                        "hostname"))
                .noneMatch(field -> String.valueOf(field.get("path")).endsWith("/Rule"));
        assertThat(driftOf()).as("build_status: the mapping and the XSLT agree again").isEmpty();
        assertThat(stepSample(pipelineUuid, raws).get("verdict")).as("regenerated, it steps clean")
                .isEqualTo("clean");
        assertThat(events()).as("the same Events as the hand edit wrote").isEqualTo(byHand);
        return byHand;
    }

    // ### 4c. the XSLT's Documentation tab cleared: the loss is named, and the mapping rebuilt from the XSLT
    private void cleared(final List<String> byHand) {
        clearDescription(xsltUuid);
        final String drift = driftOf();
        assertThat(drift).as("build_status says it was lost: %s", drift)
                .contains("was saved by stroom-mcp with the mapping (or plan) it is generated from", "it is gone");
        final String said = refusal("build_translation_xslt", args("uuid", xsltUuid, "stream_ids", raws));
        assertThat(said).as("regenerating is refused, saying why").contains("it is gone", "rebuild_mapping");
        assertThat(keptSection(readDocument(false))).as("describe_document says so too").containsKey("lost");
        // No streams: it finds them.
        final Map<String, Object> restored = agreed("rebuild_mapping", args("uuid", xsltUuid));
        dump("rebuild_mapping lost", restored);
        final Map<String, Object> summary = map(restored, "rebuilt");
        assertThat(restored.get("saved")).as("rebuilt from the XSLT alone").isNotNull();
        assertThat(restored).containsEntry("mode", "mapping lost").doesNotContainKey("differences");
        assertThat(summary).containsEntry("entries_kept", 0).doesNotContainKey("kept_as_xpath");
        final Map<String, Object> provenOn = map(restored, "proven_on");
        assertThat(new TreeSet<>(((List<Number>) provenOn.get("streams")).stream().map(Number::longValue).toList()))
                .as("proven on the build's streams (stepped, never processed: no sample filters)")
                .isEqualTo(new TreeSet<>(raws));
        assertThat((String) provenOn.get("which")).contains("newest raw streams");
        final Map<String, Object> back = keptMapping(xsltUuid);
        assertThat(list(back, "extract")).as("its key=value extractions read back from the functions that write "
                                             + "them").hasSize(24);
        assertThat(back).containsEntry("input", "json");
        assertThat(list(back, "extract").stream().map(e -> strings(e, "names").getFirst())
                .collect(Collectors.toSet())).isEqualTo(new HashSet<>(names()));
        assertThat(driftOf()).as("build_status has nothing to say").isEmpty();
        assertThat(events()).as("the same Events as before the loss").isEqualTo(byHand);
    }

    // ### 4d. an edit no mapping can express: refused with the differences, saved only once accepted
    private void inexpressible() {
        final String code = readXslt(xsltUuid).getData();
        final int deny = code.indexOf("mode=\"event_type_traffic_deny\"");
        final int end = code.indexOf("</Deny>", deny);
        final String edited = code.substring(0, end) + "<Data Name=\"tunnel\" Value=\"{*[@key='tunnel']}\"/>\n"
                              + "          " + code.substring(end);
        editXslt(xsltUuid, doc -> doc.copy().data(edited).build());
        final Map<String, Object> refused = json("rebuild_mapping", args("uuid", xsltUuid, "stream_ids", raws));
        dump("rebuild_mapping refused", refused);
        final List<String> paths = list(refused, "differences").stream()
                .map(difference -> String.valueOf(difference.get("path")))
                .toList();
        assertThat(refused.get("saved")).as("refused: nothing saved").isNull();
        assertThat(paths).as("no record has a tunnel, so the XSLT writes it empty and the mapping wouldn't")
                .anyMatch(path -> path.contains("tunnel"));
        assertThat((String) refused.get("hint")).contains("accept_differences");
        final Map<String, Object> accepted = agreed("rebuild_mapping", args("uuid", xsltUuid, "stream_ids", raws,
                "accept_differences", true));
        assertThat(accepted.get("saved")).as("accepted: saved").isNotNull();
        assertThat(driftOf()).as("the mapping and the XSLT in step").isEmpty();
    }

    // ### 5. an edit made by hand is still called one, and stepping it names the missing function
    private void missingFunction() {
        final String code = readXslt(xsltUuid).getData();
        editXslt(xsltUuid, doc -> doc.copy().data(code.replaceFirst(Pattern.quote(
                "mcp:quoted_value($body, 'logid')"), Matcher.quoteReplacement("stroom:extract($body, 'logid')")))
                .build());
        assertThat(driftOf()).as("build_status").contains("edited by hand since the server saved it");
        final Map<String, Object> stepped = stepSample(pipelineUuid, raws.subList(0, 1));
        assertThat(stepped.get("verdict")).as("stepping it").isEqualTo("blocking");
        assertThat(jsonOf(list(stepped, "groups"))).as("names the missing function").contains("function named");
        // Regenerating would undo the edit: refused, saying what it changed (asked for by the user: a hand edit
        // kept).
        final String said = refusal("build_translation_xslt", args("uuid", xsltUuid, "stream_ids", raws,
                "include_xslt", false));
        assertThat(said).as("regenerating over the edit is refused").startsWith("Not saved:")
                .contains("stroom:extract(", "discard_hand_edit");
        // The user says to drop it (it breaks the translation): saved over.
        final Map<String, Object> again = agreed("build_translation_xslt", args("uuid", xsltUuid, "stream_ids",
                raws, "include_xslt", false, "change", "Regenerated over a hand edit", "discard_hand_edit", true));
        assertThat(again.get("ok")).as("regenerated again: %s", again.get("problems")).isEqualTo(true);
        assertThat(driftOf()).as("clean").isEmpty();
    }

    // ### 6. a function call given as a field is refused
    private void functionAsField(final Map<String, Object> mapping) {
        final Map<String, Object> called = new LinkedHashMap<>(mapping);
        final List<Map<String, Object>> common = new ArrayList<>(E2eFixtures.entries(mapping));
        common.add(Map.of("path", "EventSource/User/Id", "field", "stroom:extract(body, 'user')"));
        called.put("common", common);
        final String said = refusal("build_translation_xslt", args("mapping", called, "stream_ids",
                raws.subList(0, 1), "schema_version", VERSION));
        assertThat(said).as("refused").contains("is a function call, not an input field", "extract");
    }

    // The build_status lines about the pipeline's XSLT (`drift_of`).
    private String driftOf() {
        return strings(json("build_status", args("build", BUILD)), "before_promotion").stream()
                .filter(line -> line.contains(PIPELINE) && line.contains("XSLT"))
                .collect(Collectors.joining(" "));
    }

    // The checked records' Events, canonical.
    private List<String> events() {
        final List<String> out = new ArrayList<>();
        for (final Checked check : checked) {
            out.addAll(eventsOf(pipelineUuid, check.stream(), check.records()));
        }
        return out;
    }

    // The XSLT as read_document gives it (describe_document's part of it).
    private String readDocument(final boolean wholeMapping) {
        return McpTestClient.text(client.call("read_document", args("uuid", xsltUuid, "mapping", wholeMapping
                ? true
                : null)));
    }

    // The ## Kept mapping section of read_document's answer, shown in it.
    private static Map<String, Object> keptSection(final String described) {
        assertThat(described).as("a kept mapping section").contains("## Kept mapping\n");
        String section = described.substring(described.indexOf("## Kept mapping\n") + "## Kept mapping\n".length());
        final int next = section.indexOf("\n\n## ");
        if (next >= 0) {
            section = section.substring(0, next);
        }
        return JsonUtil.readValue(section, Map.class);
    }

    // The ## Pending changes section's lines.
    private static List<String> pendingChanges(final String described) {
        assertThat(described).as("a pending changes section").contains("## Pending changes\n");
        final String section = described.substring(described.indexOf("## Pending changes\n")
                                                   + "## Pending changes\n".length());
        return section.lines()
                .takeWhile(line -> line.startsWith("- "))
                .map(line -> line.substring(2))
                .toList();
    }

    // Whether any variable is declared after other content of its template, or inside it, rather than at its start.
    private static boolean justInTime(final String code) {
        for (final Element variable : descendants(parse(code).getDocumentElement(), "variable")) {
            if (!XSL.equals(variable.getNamespaceURI())) {
                continue;
            }
            final Node parent = variable.getParentNode();
            if (!"template".equals(parent.getLocalName())) {
                if (!"stylesheet".equals(parent.getLocalName())) {
                    return true;
                }
                continue;
            }
            for (Node sibling = variable.getPreviousSibling(); sibling != null;
                    sibling = sibling.getPreviousSibling()) {
                if (sibling instanceof final Element element
                    && !(XSL.equals(element.getNamespaceURI()) && "variable".equals(element.getLocalName()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int occurrences(final String text, final String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) {
            count++;
        }
        return count;
    }

    private static List<String> names() {
        final List<String> names = new ArrayList<>(QUOTED);
        names.addAll(BARE);
        return names;
    }

    // The three streams' records: traffic, the VPN records only in the last.
    private static List<List<Map<String, Object>>> streams() {
        final List<Map<String, Object>> third = new ArrayList<>(IntStream.range(270, 320)
                .mapToObj(n -> traffic(n, ACTIONS.get(n % 3)))
                .toList());
        IntStream.range(0, 6).forEach(n -> third.add(vpn(n, n % 2 == 0
                ? "negotiate"
                : "tunnel-stats")));
        return List.of(
                IntStream.range(0, 150).mapToObj(n -> traffic(n, ACTIONS.get(n % 3))).toList(),
                IntStream.range(150, 270).mapToObj(n -> traffic(n, ACTIONS.get(n % 3))).toList(),
                third);
    }

    private static Map<String, Object> traffic(final int n, final String action) {
        final String time = "%02d:%02d".formatted(n / 60 % 60, n % 60);
        final String body = "date=2026-10-01 time=10:" + time + " eventtime=17909317" + "%010d".formatted(n)
                            + " tz=\"+1000\" logid=\"000000" + (n % 3) + "013\" type=\"traffic\" subtype=\""
                            + (n % 2 == 1
                ? "forward"
                : "local") + "\" level=\"notice\" vd=\"root\" srcip=10.0." + (n % 7) + "." + (n % 250 + 1)
                            + " srcport=" + (1024 + n) + " srcintf=\"port" + (n % 3) + "\" srcintfrole=\"lan\" "
                            + "dstip=192.0.2." + (n % 200 + 1) + " dstport=" + List.of(443, 53, 22).get(n % 3)
                            + " dstintf=\"wan1\" dstintfrole=\"wan\" policyid=" + (n % 5) + " proto="
                            + List.of(6, 17, 1).get(n % 3) + " action=\"" + action + "\" service=\""
                            + List.of("HTTPS", "DNS", "SSH").get(n % 3) + "\"";
        return record(189, "2026-10-01T10:" + time + "Z", body);
    }

    private static Map<String, Object> vpn(final int n, final String action) {
        final String body = "date=2026-10-01 time=11:00:" + "%02d".formatted(n) + " eventtime=17909400"
                            + "%010d".formatted(n) + " tz=\"+1000\" logid=\"0101037124\" type=\"event\" "
                            + "subtype=\"vpn\" level=\"error\" vd=\"root\" logdesc=\"IPsec phase 1 error\" action=\""
                            + action + "\" remip=203.0.113." + (n + 1) + " locip=198.51.100.2 remport=500 "
                            + "locport=500 outintf=\"wan1\" vpntunnel=\"hq-" + n + "\" status=\""
                            + ("negotiate".equals(action)
                ? "failure"
                : "success") + "\"";
        return record(187, "2026-10-01T11:00:" + "%02d".formatted(n) + "Z", body);
    }

    private static Map<String, Object> record(final int priority, final String timestamp, final String body) {
        final Map<String, Object> record = new LinkedHashMap<>();
        record.put("priority", priority);
        record.put("timestamp", timestamp);
        record.put("hostname", "fw01");
        record.put("body", body);
        return record;
    }

    private static Map<String, Object> network(final String name,
                                               final List<String> actions,
                                               final String element,
                                               final String success) {
        final String at = "EventDetail/Network/" + element;
        final Map<String, Object> proto = Map.of("map", Map.of("1", "ICMP", "6", "TCP", "17", "UDP"), "default",
                "Other");
        final List<Map<String, Object>> fields = new ArrayList<>(List.of(
                Map.of("path", "EventDetail/TypeId", "value", name),
                Map.of("path", at + "/Source/Device/IPAddress", "field", "srcip"),
                Map.of("path", at + "/Source/Port", "field", "srcport"),
                with(Map.of("path", at + "/Source/TransportProtocol", "field", "proto"), proto),
                Map.of("path", at + "/Destination/Device/IPAddress", "field", "dstip"),
                Map.of("path", at + "/Destination/Port", "field", "dstport"),
                with(Map.of("path", at + "/Destination/TransportProtocol", "field", "proto"), proto),
                Map.of("path", at + "/Destination/ApplicationProtocol", "field", "service"),
                Map.of("path", at + "/Rule", "field", "policyid"),
                Map.of("path", at + "/Outcome/Success", "value", success)));
        for (final String key : List.of("logid", "subtype", "level", "vd", "srcintf", "srcintfrole", "dstintf",
                "dstintfrole")) {
            fields.add(Map.of("path", at + "/Data", "data_name", key, "field", key));
        }
        return Map.of("name", name, "when", List.of(Map.of("field", "action", "one_of", actions)), "fields",
                fields);
    }

    private static Map<String, Object> mapping() {
        final List<Map<String, Object>> extract = new ArrayList<>();
        QUOTED.forEach(key -> extract.add(Map.of("field", "body", "regex", "(?:^|\\s)" + key + "=\"([^\"]*)\"",
                "names", List.of(key))));
        BARE.forEach(key -> extract.add(Map.of("field", "body", "regex", "(?:^|\\s)" + key + "=(\\S+)",
                "names", List.of(key))));
        final List<Map<String, Object>> vpnFields = new ArrayList<>(List.of(
                Map.of("path", "EventDetail/TypeId", "value", "VpnConnect"),
                Map.of("path", "EventDetail/Description", "field", "logdesc"),
                Map.of("path", "EventDetail/Network/Connect/Source/Device/IPAddress", "field", "locip"),
                Map.of("path", "EventDetail/Network/Connect/Source/Port", "field", "locport"),
                Map.of("path", "EventDetail/Network/Connect/Destination/Device/IPAddress", "field", "remip"),
                Map.of("path", "EventDetail/Network/Connect/Destination/Port", "field", "remport"),
                Map.of("path", "EventDetail/Network/Connect/Outcome/Success", "field", "action",
                        "map", Map.of("negotiate", "false", "tunnel-stats", "true"))));
        for (final String key : List.of("logid", "vpntunnel", "status", "outintf")) {
            vpnFields.add(Map.of("path", "EventDetail/Network/Connect/Data", "data_name", key, "field", key));
        }
        final Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("input", "json");
        mapping.put("json_layout", "array");
        mapping.put("unmatched", "warn");
        mapping.put("extract", extract);
        mapping.put("common", List.of(
                Map.of("path", "EventTime/TimeCreated", "field", "timestamp", "time_format",
                        "yyyy-MM-dd'T'HH:mm:ssX"),
                Map.of("path", "EventSource/System/Name", "value", "FortiOS"),
                Map.of("path", "EventSource/System/Environment", "value", "Test"),
                Map.of("path", "EventSource/Generator", "value", "FortiOS"),
                Map.of("path", "EventSource/Device/HostName", "field", "hostname")));
        mapping.put("events", List.of(
                network("traffic-deny", List.of("deny"), "Deny", "false"),
                network("traffic-permit", List.of("accept"), "Permit", "true"),
                network("traffic-close", List.of("close"), "Close", "true"),
                Map.of("name", "vpn", "when", List.of(Map.of("field", "action", "one_of",
                        List.of("negotiate", "tunnel-stats"))), "fields", vpnFields)));
        return mapping;
    }

    private static Map<String, Object> with(final Map<String, Object> entry, final Map<String, Object> more) {
        final Map<String, Object> out = new HashMap<>(entry);
        out.putAll(more);
        return out;
    }


    // --------------------------------------------------------------------------------


    /// Records of a stream whose Events are compared before and after each change.
    private record Checked(long stream, List<Integer> records) {

    }
}
