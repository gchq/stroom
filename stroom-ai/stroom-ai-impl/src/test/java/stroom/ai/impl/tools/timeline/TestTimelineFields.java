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

import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.instructions.AgentInstructions;
import stroom.ai.impl.tools.instructions.AgentKnowledge;
import stroom.ai.impl.tools.instructions.InstructionRules;
import stroom.ai.impl.tools.instructions.InstructionRules.FieldRule;
import stroom.ai.impl.tools.query.DataSourceAccess;
import stroom.ai.impl.tools.timeline.TimelineFields.Found;
import stroom.ai.impl.tools.timeline.TimelineFields.Origin;
import stroom.docref.DocRef;
import stroom.search.elastic.shared.ElasticIndexDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// Which field of each index holds the user, in order: the call's (or skill's), the AGENTS doc's `## Fields`, a
/// field convention's, the index's documentation; each index on its own.
class TestTimelineFields {

    private static final DocRef PROXY = new DocRef(ElasticIndexDoc.TYPE, "proxy-uuid", "Proxy");
    private static final ToolSession SESSION = ToolSession.chat(1);

    private final AgentInstructions agentInstructions = Mockito.mock(AgentInstructions.class);
    private final DataSourceAccess dataSourceAccess = Mockito.mock(DataSourceAccess.class);
    private final AgentKnowledge knowledge = new AgentKnowledge() {
        @Override
        public List<String> guideNames() {
            return List.of();
        }

        @Override
        public Optional<String> guide(final String name) {
            return Optional.empty();
        }

        @Override
        public List<String> conventionNames() {
            return List.of("ecs", "stroom-flat");
        }

        @Override
        public Optional<String> convention(final String name) {
            return Optional.of("ecs".equals(name)
                    ? "field_map:\n  EventSource/Device/HostName: {name: host.name, type: keyword}\n"
                      + "  EventSource/User/Id: {name: user.name, type: keyword}\n"
                    : "field_map:\n  EventSource/User/Id: {name: UserId, type: keyword}\n");
        }
    };
    private final TimelineFields fields = new TimelineFields(agentInstructions, dataSourceAccess,
            Optional.of(knowledge));

    @Test
    void theCallsFieldComesFirstWhereTheIndexHasIt() {
        rule("Initiator.Id");

        assertThat(find(List.of("SrcUser"), Set.of("SrcUser", "Initiator.Id", "user.name")))
                .hasValue(new Found("SrcUser", Origin.CALL, null));
        // One the index hasn't is passed over, per index.
        assertThat(find(List.of("SrcUser"), Set.of("Initiator.Id", "user.name")))
                .hasValue(new Found("Initiator.Id", Origin.AGENTS, "/Indexes"));
    }

    @Test
    void theAgentsDocsFieldsComeBeforeTheConventions() {
        rule("Initiator.Id", "Actor.Id");

        assertThat(find(List.of(), Set.of("Actor.Id", "user.name"))).hasValue(new Found("Actor.Id", Origin.AGENTS,
                "/Indexes"));
    }

    @Test
    void aConventionsUserFieldIsUsedWhereNothingElseSays() {
        assertThat(find(List.of(), Set.of("UserId", "HostName"))).hasValueSatisfying(found -> {
            assertThat(found.field()).isEqualTo("UserId");
            assertThat(found.origin()).isEqualTo(Origin.CONVENTION);
            assertThat(found.from()).isEqualTo("the field convention (stroom-flat)");
        });
        assertThat(fields.find(SESSION, PROXY, InstructionRules.HOST_FIELD, List.of(), Set.of("host.name")::contains))
                .hasValue(new Found("host.name", Origin.CONVENTION, "ecs"));
    }

    @Test
    void theIndexsDocumentationIsReadLast() {
        Mockito.when(dataSourceAccess.documentation(PROXY)).thenReturn(Optional.of("""
                # Proxy logs
                Each request's time is `@timestamp`.
                The user who made the request is in `cs_username`.
                """));

        assertThat(find(List.of(), Set.of("@timestamp", "cs_username")))
                .hasValue(new Found("cs_username", Origin.DOCUMENTATION, null));
        assertThat(find(List.of(), Set.of("@timestamp"))).isEmpty();
    }

    @Test
    void aConventionFileIsReadByItsPaths() {
        assertThat(TimelineFields.conventionField("field_map:\n  EventSource/User/Id: {name: UserId, type: k}\n",
                "EventSource/User/Id")).hasValue("UserId");
        assertThat(TimelineFields.conventionField("field_map:\n", "EventSource/User/Id")).isEmpty();
    }

    private Optional<Found> find(final List<String> given, final Set<String> indexFields) {
        return fields.find(SESSION, PROXY, InstructionRules.USER_FIELD, given, indexFields::contains);
    }

    private void rule(final String... names) {
        Mockito.when(agentInstructions.fieldsRuled(SESSION, PROXY)).thenReturn(Map.of(InstructionRules.USER_FIELD,
                new FieldRule(List.of(names), "/Indexes")));
    }
}
