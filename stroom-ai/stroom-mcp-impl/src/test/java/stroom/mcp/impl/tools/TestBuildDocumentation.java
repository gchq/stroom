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

package stroom.mcp.impl.tools;

import stroom.ai.impl.ToolOutputs;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.fielddoc.Descriptions;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestBuildDocumentation {

    @Test
    void theLayoutTheUserChoseWhereTheFeedsAgentsDocsDifferIsUsed() {
        // Decided by the user (2026-11-25): asked per conflict (compare_feeds), kept in the build and used.
        final DocumentationWorld docs = new DocumentationWorld();
        final BuildWorld.Node folder = docs.world.folder("System/" + BuildWorld.WORKSPACE + "/acme");
        final BuildWorld.Node pipeline = docs.world.add(PipelineDoc.TYPE, "ACME-Events", folder.uuid, null);
        final DocRef ref = new DocRef(PipelineDoc.TYPE, pipeline.uuid, "ACME-Events");
        final ToolSession session = BuildToolCalls.context().session();
        assertThat(docs.docs.layout(session, ref, null).layout()).isEqualTo("by_event_type");

        docs.world.records().rememberFeedComparison("acme", Map.of("agents", Map.of(AgentsConflicts.DOCUMENTATION,
                Map.of("value", "compact", "folder", "/Feeds/Acme/Site-A"))));

        final BuildDocumentation.Layout chosen = docs.docs.layout(session, ref, null);
        assertThat(chosen.layout()).isEqualTo("compact");
        assertThat(String.valueOf(chosen)).contains("as the user chose for the build's feeds",
                "/Feeds/Acme/Site-A");
        // Asked for in the call: that wins.
        assertThat(docs.docs.layout(session, ref, "by_event_type").layout()).isEqualTo("by_event_type");
    }

    @Test
    void fieldsAreAskedToBeDescribedARoundAtATime() {
        // Seen in testing: 75 fields asked for at once, and the agent tried again and again to send every
        // description, with the markdown, in one call, which its context couldn't hold.
        final Descriptions described = Descriptions.asking(true);
        for (int i = 0; i < 60; i++) {
            described.of("field" + i, "EventDetail/Data[@Name='field" + i + "']/@Value", List.of("a", "b"), "");
        }
        final Map<String, Object> first = BuildDocumentation.needsDescriptions(described, List.of());

        assertThat((List<?>) first.get("fields")).hasSize(BuildDocumentation.DESCRIPTIONS_ROUND);
        assertThat(String.valueOf(first.get("hint"))).contains("the first 25 of the 60 to describe", "no markdown");

        // A last round of fewer says nothing of more to come.
        final Descriptions few = Descriptions.asking(true);
        few.of("one", "EventSource/User/Id", List.of("alice"), "");
        assertThat(String.valueOf(BuildDocumentation.needsDescriptions(few, List.of()).get("hint")))
                .doesNotContain("the rest follow");
    }

    @Test
    void aRoundOfLongFieldsHasNoMoreThanTheChatGivesInFull() {
        // Seen in chats 184 to 186: 25 fields of 7,000 to 8,000 characters, cut at 4,000 in the chat's stub 11
        // times, and the rest read back from the saved result 30 times.
        final Descriptions described = Descriptions.asking(true);
        for (int i = 0; i < 40; i++) {
            described.of("EventDetail/View/Folder/Path" + i, "the item; for each item (each item of `event.Folders`) "
                                                           + "(view_mailitems)",
                    List.of("AAMk32B9462D427DC07FB8B6CF8503817067\\Drafts<37fd1f4c-f7fe-d8",
                            "AAMkAF14180BDCE651C572BC988992027D95\\Archive<0a55edf9-fbb9-2",
                            "AAMk7100073D470F0A1BBF8570BEB49C1F88\\Archive<5552a874-257e-6"),
                    "The full system file path.");
        }

        final Map<String, Object> round = BuildDocumentation.needsDescriptions(described, List.of());

        final List<?> fields = (List<?>) round.get("fields");
        assertThat(fields).hasSizeBetween(15, BuildDocumentation.DESCRIPTIONS_ROUND - 1);
        assertThat(String.valueOf(round.get("hint"))).contains("the first " + fields.size() + " of the 40 to "
                                                               + "describe");
        final String path = "/outputs/turn-1/call-2.txt";
        final String stub = ToolOutputs.stub(ToolOutputs.forSaving(JsonUtil.writeValueAsString(round, false)), path,
                null);
        assertThat(stub).contains("\"field\":\"EventDetail/View/Folder/Path" + (fields.size() - 1) + "\"")
                .doesNotContain("more: read them from " + path)
                .doesNotContain("\"field\":\"EventDetail/View/Folder/Path" + fields.size() + "\"");
    }

    @Test
    void aFieldLongerThanARoundsRoomIsARoundOfItsOwn() {
        final Descriptions described = Descriptions.asking(true);
        // Its name alone past the room (no part of a field is cut so far).
        described.of("Data/" + "a".repeat(BuildDocumentation.DESCRIPTIONS_ROUND_CHARS), "`type`", List.of("a"), "");
        described.of("Description", "the description", List.of("b"), "");

        final Map<String, Object> round = BuildDocumentation.needsDescriptions(described, List.of());

        assertThat((List<?>) round.get("fields")).hasSize(1);
        assertThat(String.valueOf(round.get("hint"))).contains("the first 1 of the 2 to describe");
    }

    @Test
    void aFromListingEveryRuleIsCutToItsFirstLines() {
        // Seen in eval case 68 (300 event types): a TypeId's and a Description's from each listing 300 rules, each
        // then a round of its own, and the round after the sixth still asking.
        final Descriptions described = Descriptions.asking(true);
        final StringBuilder from = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            from.append("\"Process-Code").append(i).append("\" (process_code").append(i).append(")\n");
        }
        described.of("EventDetail/TypeId", from.toString().strip(), List.of("Process-Code1"), "");
        described.of("EventDetail/Description", from.toString().strip(), List.of("Code 1 was run"), "");
        described.of("EventSource/User/Id", "`user`", List.of("alice"), "");

        final Map<String, Object> round = BuildDocumentation.needsDescriptions(described, List.of());

        final List<Map<String, Object>> fields = JsonValues.maps(round.get("fields"));
        assertThat(fields).hasSize(3);
        final String typeIdFrom = String.valueOf(fields.getFirst().get("from"));
        assertThat(typeIdFrom).startsWith("\"Process-Code0\" (process_code0)\n")
                .endsWith(" more line(s) like these")
                .doesNotContain("Process-Code299");
        assertThat(typeIdFrom.length()).isLessThanOrEqualTo(BuildDocumentation.FROM_MOST + 50);
        assertThat(fields.getLast()).containsEntry("from", "`user`");
    }

    @Test
    void descriptionsAreAskedNotToGuessCodesOrWhoseAnIdIs() {
        // Seen in chat 182: RecordType "15 for Exchange, 25 for Teams", not borne out by the sample, and the audit
        // record's Id described as the item the action was taken on.
        final Descriptions few = Descriptions.asking(true);
        few.of("RecordType", "EventDetail/Data[@Name='RecordType']/@Value", List.of("15", "25"), "");
        assertThat(String.valueOf(BuildDocumentation.needsDescriptions(few, List.of()).get("hint")))
                .contains("give no code a meaning (15 = Exchange) the values or source notes don't show",
                        "say whose an Id is only where its name or the source notes do");
    }

    @Test
    void errorsOfTheSameKindInSeveralStreamsAreOneRow() {
        // Seen in chat 182: "String value truncated to length 10000" and "did not parse as XML" each listed once
        // per stream, with a different example.
        final List<Map<String, Object>> groups = List.of(
                group("review", "jsonParser", "WARNING", 6, "String value truncated to length 10000, original "
                                                            + "length: 25486"),
                group("benign", "translationFilter", "WARNING", 3, "event.Body.Content did not parse as XML: "
                                                                  + "record 148"),
                group("review", "jsonParser", "WARNING", 2, "String value truncated to length 10000, original "
                                                            + "length: 27985"),
                group("benign", "translationFilter", "WARNING", 1, "event.Body.Content did not parse as XML: "
                                                                  + "record 1815"),
                group("review", "jsonParser", "ERROR", 1, "String value truncated to length 10000, original "
                                                          + "length: 1"));
        final List<Map<String, Object>> merged = BuildDocumentation.acrossStreams(groups);

        assertThat(merged).extracting(g -> g.get("severity") + " " + g.get("count")).containsExactly(
                "WARNING 8", "ERROR 1", "WARNING 4");
        final String section = BuildDocumentation.errorsSection(merged, List.of());
        assertThat(section).contains("| review | `jsonParser` | WARNING | 8 | String value truncated to length 10000, "
                                     + "original length: 25486 |",
                        "| benign | `translationFilter` | WARNING | 4 | event.Body.Content did not parse as XML: "
                        + "record 148 |")
                .doesNotContain("27985", "record 1815");
    }

    private static Map<String, Object> group(final String errorClass,
                                             final String element,
                                             final String severity,
                                             final int count,
                                             final String message) {
        final Map<String, Object> group = new LinkedHashMap<>();
        group.put("class", errorClass);
        group.put("severity", severity);
        group.put("element", element);
        group.put("count", count);
        group.put("examples", List.of(Map.of("message", message)));
        return group;
    }
}
