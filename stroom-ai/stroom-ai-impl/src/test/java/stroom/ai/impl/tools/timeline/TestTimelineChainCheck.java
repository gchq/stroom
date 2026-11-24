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

package stroom.ai.impl.tools.timeline;

import stroom.ai.api.ElasticEql;
import stroom.ai.api.ElasticKeyStreams;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.instructions.AgentInstructions;
import stroom.ai.impl.tools.query.DataSourceAccess;
import stroom.docref.DocRef;
import stroom.search.elastic.shared.ElasticIndexDoc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestTimelineChainCheck {

    private static final ToolSession SESSION = ToolSession.chat(1);
    private static final DocRef INDEX = new DocRef(ElasticIndexDoc.TYPE, "activity", "User activity");
    private static final List<EventChains.Entry> ENTRIES = List.of(EventChains.Entry.role("User activity",
            "Lock-Workstation", "idle"));

    private final ElasticKeyStreams keyStreams = Mockito.mock(ElasticKeyStreams.class);
    private final DataSourceAccess dataSourceAccess = Mockito.mock(DataSourceAccess.class);
    private final AgentInstructions agentInstructions = Mockito.mock(AgentInstructions.class);
    private TimelineChainCheck check;

    @BeforeEach
    void setUp() {
        Mockito.when(dataSourceAccess.find("User activity")).thenReturn(List.of(INDEX));
        Mockito.when(dataSourceAccess.documentation(INDEX)).thenReturn(Optional.empty());
        // No AGENTS doc's ## Fields, and no field convention: only the skill can say which field holds the user.
        Mockito.when(agentInstructions.fieldsRuled(Mockito.any(), Mockito.any())).thenReturn(Map.of());
        // The index has no StreamId and EventId, so the check stops once the user field is found.
        Mockito.when(keyStreams.target(List.of(INDEX))).thenReturn(new ElasticKeyStreams.Target("9", List.of(
                new ElasticKeyStreams.Doc(INDEX, "user-activity", "EventTime", Map.of(
                        "EventTime", new ElasticEql.Field("EventTime", "date", null, null, false),
                        "UserName", new ElasticEql.Field("UserName", "keyword", null, null, false))))));
        check = new TimelineChainCheck(() -> keyStreams, dataSourceAccess, Mockito.mock(TimelineEventXml.class),
                new TimelineFields(agentInstructions, dataSourceAccess, Optional.empty()));
    }

    @Test
    void aSkillsOwnUserFieldIsUsedWithNoAgentsDoc() {
        final TimelineChainCheck.GivenFields given = TimelineChainCheck.GivenFields.of(List.of(Map.of(
                "entity", "{user}", "userField", "UserName")));

        assertThat(check.check(SESSION, ENTRIES, given)).isEmpty();
    }

    @Test
    void aSourcesOwnUserFieldIsUsedWithNoAgentsDoc() {
        final TimelineChainCheck.GivenFields given = TimelineChainCheck.GivenFields.of(List.of(Map.of(
                "sources", List.of(Map.of("index", "user activity", "user", "UserName")))));

        assertThat(check.check(SESSION, ENTRIES, given)).isEmpty();
    }

    @Test
    void withNoUserFieldAnywhereTheCheckSaysWhereToGiveOne() {
        assertThat(check.check(SESSION, ENTRIES, TimelineChainCheck.GivenFields.NONE)).singleElement()
                .asString()
                .contains("has no field holding the user", "activity_timeline call's userField",
                        "AGENTS doc's ## Fields");
    }

    @Test
    void aParameterIsNoField() {
        final TimelineChainCheck.GivenFields given = TimelineChainCheck.GivenFields.of(List.of(Map.of(
                "userField", "{userField}", "sources", List.of(Map.of("index", "User activity", "user",
                        "[[user field]]")))));

        assertThat(given.users("User activity")).isEmpty();
        assertThat(check.check(SESSION, ENTRIES, given)).singleElement().asString()
                .contains("has no field holding the user");
    }
}
