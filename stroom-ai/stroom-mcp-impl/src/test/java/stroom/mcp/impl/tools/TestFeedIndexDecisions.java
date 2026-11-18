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

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.indexing.FeedIndexes;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.PlannedField;
import stroom.docref.DocRef;
import stroom.meta.shared.Meta;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/// What an aggregated feed's indexes say and are named with, outside Stroom: each source type group's Events get an
/// index of their own, another group's shared only at the user's word. The end-to-end behaviour is
/// TestE2eSourceTypeIndexes'.
class TestFeedIndexDecisions {

    private static final String BUILD = "pega-2";
    private static final String FEED = "PEGA";
    private static final String SECURITY = "PEGA security";
    private static final String MAIL = "PEGA mail";
    private static final String SECURITY_INDEX = "pega-security-events";
    private static final long MAIL_EVENTS = 10L;

    private SourceTypeGroups groups;
    private StreamReader streamReader;
    private FeedIndexDecisions decisions;
    private Map<String, Object> state;

    @BeforeEach
    void setUp() {
        groups = mock(SourceTypeGroups.class);
        streamReader = mock(StreamReader.class);
        decisions = new FeedIndexDecisions(groups, mock(SourceTypeRegistries.class), streamReader,
                mock(BuildRecords.class), mock(WriteGuard.class), mock(DocumentAccess.class), mock(Consent.class),
                mock(IndexFields.class), () -> mock(BuildPlan.class));
        // Two groups whose Events go into the feed's own, the security group's index promoted by an earlier build.
        final List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("PegaSecurityEvent", SECURITY, "p-security"));
        rows.add(row("PegaMail", MAIL, "p-mail"));
        state = new LinkedHashMap<>();
        state.put("feed", FEED);
        state.put("rows", rows);
        FeedIndexes.record(state, SECURITY, FEED, plan(SECURITY_INDEX, PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("UserId", "keyword", "EventSource/User/Id"), PlannedField.of("TypeId", "keyword",
                        "EventDetail/TypeId")), null, new FeedIndexes.Decided("user", "2026-11-14T10:00:00Z",
                "pega-1"));
        when(groups.registry(BUILD, List.of())).thenReturn(new SourceTypeGroups.Registry(FEED,
                new SourceTypeRegistries.Found(null, false, state)));
        when(groups.scopedGroup(BUILD, List.of(FEED))).thenReturn(Optional.of(MAIL));
        when(streamReader.meta(MAIL_EVENTS)).thenReturn(Meta.builder()
                .id(MAIL_EVENTS)
                .feedName(FEED)
                .typeName("Events")
                .pipelineUuid("p-mail")
                .build());
    }

    @Test
    void aGroupsEventsGetAnIndexOfTheirOwnThoughAnotherGroupsGoIntoTheSameFeed() {
        final FieldPlan mine = plan("pega-mail-events", PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("Direction", "keyword", "EventDetail/TypeId"));

        final FeedIndexDecisions.ForPlan forPlan = decisions.forPlan(call("{}"), null, BUILD, List.of(MAIL_EVENTS),
                mine, "draft_index_mapping");

        // Nothing of the security group's inherited, nothing clashing.
        assertThat(forPlan.plan()).isEqualTo(mine);
        assertThat(forPlan.said()).containsEntry("output_feed", FEED)
                .containsEntry("group", MAIL)
                .containsEntry("groups", List.of(MAIL))
                .containsEntry("index", "pega-mail-events")
                .doesNotContainKeys("inherited_fields", "shared_with", "index_clashes");
        assertThat(String.valueOf(forPlan.said().get("kept"))).contains("an index of its own",
                "source_pipeline_uuid");
    }

    @Test
    void anotherGroupsIndexIsRefusedUnlessTheUserAskedToShareIt() {
        assertThatThrownBy(() -> decisions.forPlan(call("{}"), null, BUILD, List.of(MAIL_EVENTS),
                plan(SECURITY_INDEX, PlannedField.of("StreamId", "id", "@StreamId")), "draft_index_mapping"))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("is the index of group(s) " + SECURITY)
                .hasMessageContaining("an index of its own")
                .hasMessageContaining("'pega-mail-events', say")
                .hasMessageContaining("share_index_with='" + SECURITY + "'");
        assertThatThrownBy(() -> decisions.forPlan(call("{\"share_index_with\": \"PEGA nothing\"}"), null, BUILD,
                List.of(MAIL_EVENTS), plan(SECURITY_INDEX, PlannedField.of("StreamId", "id", "@StreamId")),
                "draft_index_mapping"))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("names no index of feed 'PEGA'")
                .hasMessageContaining(SECURITY_INDEX + " (group(s) " + SECURITY + ")");
    }

    @Test
    void anIndexSharedAtTheUsersWordIsTheUnionOfTheGroupsFieldsAndItsClashesAreAsked() {
        final FieldPlan shared = plan(SECURITY_INDEX, PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("TypeId", "long", "EventDetail/TypeId"));

        final FeedIndexDecisions.ForPlan forPlan = decisions.forPlan(call("{\"share_index_with\": \"" + SECURITY
                                                                           + "\"}"), null, BUILD,
                List.of(MAIL_EVENTS), shared, "draft_index_mapping");

        assertThat(forPlan.plan().fields()).extracting(PlannedField::name).contains("StreamId", "TypeId", "UserId");
        assertThat(forPlan.said()).containsEntry("shared_with", List.of(SECURITY))
                .containsEntry("groups", List.of(SECURITY, MAIL))
                .containsEntry("inherited_fields", List.of("UserId"));
        // No form here: the clash is said for the agent to ask about.
        assertThat(String.valueOf(forPlan.said().get("index_clashes"))).contains("Field TypeId: long",
                "keyword in index " + SECURITY_INDEX);
        // Named by its index, as by its group.
        assertThat(decisions.forPlan(call("{\"share_index_with\": \"" + SECURITY_INDEX + "\"}"), null, BUILD,
                List.of(MAIL_EVENTS), plan(SECURITY_INDEX, PlannedField.of("StreamId", "id", "@StreamId")),
                "draft_index_mapping").said()).containsEntry("shared_with", List.of(SECURITY));
    }

    @Test
    void theBuildsGroupsIndexIsSuggestedNamedLikeTheFeedsFirst() {
        assertThat(decisions.suggestedIndexName(BUILD)).contains("pega-mail-events");
    }

    @Test
    void aGroupsFieldsOfItsOwnEndWithItsNameInLowerCase() {
        assertThat(FeedIndexDecisions.suffixOf("PEGA integration")).isEqualTo("pega_integration");
        assertThat(FeedIndexDecisions.suffixOf(" Mail / Audit (v2) ")).isEqualTo("mail_audit_v2");
        assertThat(FeedIndexDecisions.suffixOf(null)).isEmpty();
    }

    @Test
    void aChangeToASharedDecisionIsTheUsersOnlyWhenSaidSo() {
        assertThat(FeedIndexDecisions.changeAsked(new ToolCall("1", "draft_index_mapping",
                "{\"change_shared\": true}"))).isTrue();
        assertThat(FeedIndexDecisions.changeAsked(new ToolCall("1", "draft_index_mapping", "{}"))).isFalse();
        assertThat(FeedIndexDecisions.changeAsked(null)).isFalse();
    }

    @Test
    void whereTheSharedDecisionsAreKeptIsSaid() {
        assertThat(FeedIndexDecisions.kept("PEGA")).isEqualTo("the registry 'PEGA source types' (its machine "
                                                              + "block: the feed's shared decisions)");
    }

    @Test
    void eachIndexChangeIsSaidForTheApproval() {
        final FieldPlan plan = new FieldPlan(FieldPlan.LUCENE, "pega-events", "EventTime", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"), PlannedField.of("Direction", "keyword",
                        "EventDetail/TypeId")), List.of(), null, List.of(), List.of(), true, null, List.of(), null,
                Map.of(), null);
        final DocRef doc = new DocRef("Index", "u-1", "pega-events");

        assertThat(new FeedIndexDecisions.IndexChange("PEGA", "PEGA mail", "PEGA", plan, List.of("Direction"), doc,
                false, "pega-events").said()).isEqualTo("index pega-events (group PEGA mail, feed PEGA): adds "
                                                        + "Direction (forward only: documents indexed before don't "
                                                        + "have them)");
        assertThat(new FeedIndexDecisions.IndexChange("PEGA", "PEGA mail", "PEGA", plan, List.of("StreamId",
                "Direction"), doc, true, "").said()).isEqualTo("index pega-events (group PEGA mail, feed PEGA): "
                                                               + "recorded as its index, with 2 fields");
        assertThat(new FeedIndexDecisions.IndexChange("PEGA", "PEGA mail", "PEGA", plan.withIndexName(
                "pega-events-v2"), List.of(), null, true, "pega-events").said())
                .isEqualTo("index pega-events-v2 (group PEGA mail, feed PEGA): a new version of pega-events, with 2 "
                           + "fields, from now on");
    }

    private static ToolCall call(final String arguments) {
        return new ToolCall("1", "draft_index_mapping", arguments);
    }

    private static FieldPlan plan(final String index, final PlannedField... fields) {
        return new FieldPlan(FieldPlan.LUCENE, index, "EventTime", List.of(fields), List.of(), null, List.of(),
                List.of(), true, "stroom-flat", List.of(), null, Map.of(), null);
    }

    private static Map<String, Object> row(final String name, final String group, final String pipeline) {
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("match", "value");
        row.put("value", name);
        row.put("status", "translated");
        row.put("group", group);
        row.put("pipeline", Map.of("name", FEED + " " + group, "uuid", pipeline));
        return row;
    }
}
