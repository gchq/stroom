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

import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.PlannedField;
import stroom.docref.DocRef;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// What an aggregated feed's indexes say and are named with, outside Stroom: the end-to-end behaviour is
/// TestE2eSourceTypeIndexes'.
class TestFeedIndexDecisions {

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

        assertThat(new FeedIndexDecisions.IndexChange("PEGA", "PEGA", plan, List.of("Direction"), doc, false,
                "pega-events").said()).isEqualTo("index pega-events (feed PEGA): adds Direction (forward only: "
                                                 + "documents indexed before don't have them)");
        assertThat(new FeedIndexDecisions.IndexChange("PEGA", "PEGA", plan, List.of("StreamId", "Direction"), doc,
                true, "").said()).isEqualTo("index pega-events (feed PEGA): recorded as its index, with 2 fields");
        assertThat(new FeedIndexDecisions.IndexChange("PEGA", "PEGA", plan.withIndexName("pega-events-v2"),
                List.of(), null, true, "pega-events").said())
                .isEqualTo("index pega-events-v2 (feed PEGA): a new version of pega-events, with 2 fields, from now "
                           + "on");
    }
}
