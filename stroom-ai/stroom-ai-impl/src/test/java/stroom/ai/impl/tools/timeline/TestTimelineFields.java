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
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.PlannedField;
import stroom.ai.impl.tools.instructions.AgentInstructions;
import stroom.ai.impl.tools.instructions.AgentKnowledge;
import stroom.ai.impl.tools.instructions.InstructionRules;
import stroom.ai.impl.tools.instructions.InstructionRules.FieldRule;
import stroom.ai.impl.tools.query.DataSourceAccess;
import stroom.ai.impl.tools.query.DataSourceDocumentation;
import stroom.ai.impl.tools.query.DataSourceDocumentation.FeedingPlans;
import stroom.ai.impl.tools.timeline.TimelineFields.Found;
import stroom.ai.impl.tools.timeline.TimelineFields.Origin;
import stroom.docref.DocRef;
import stroom.search.elastic.shared.ElasticIndexDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// Which field of each index holds the user, in order: the call's (or skill's), the AGENTS doc's `## Fields`, the
/// index plan of a pipeline feeding it, a field convention's, the index's documentation; each index on its own.
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
    private final DataSourceDocumentation documentation = Mockito.mock(DataSourceDocumentation.class);
    private final TimelineFields fields = new TimelineFields(agentInstructions, dataSourceAccess,
            Optional.of(knowledge), () -> documentation);

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
    void aConventionsUserFieldIsUsedWhereNothingElseSaysWithTheFieldsLineThatWouldMakeItTheSites() {
        Mockito.when(agentInstructions.folderOf(PROXY)).thenReturn("/Indexes/Proxy");

        assertThat(find(List.of(), Set.of("UserId", "HostName"))).hasValueSatisfying(found -> {
            assertThat(found.field()).isEqualTo("UserId");
            assertThat(found.origin()).isEqualTo(Origin.CONVENTION);
            assertThat(found.from()).isEqualTo("the field convention (stroom-flat)");
            assertThat(found.suggestion()).isEqualTo("To make UserId the user field here, add `- user: UserId` "
                                                     + "under `## Fields` in an AGENTS doc in /Indexes/Proxy (with "
                                                     + "propose_instructions).");
        });
        assertThat(fields.find(SESSION, PROXY, InstructionRules.HOST_FIELD, List.of(), Set.of("host.name")::contains))
                .hasValue(new Found("host.name", Origin.CONVENTION, "ecs", "To make host.name the host field here, "
                                                                           + "add `- host: host.name` under `## "
                                                                           + "Fields` in an AGENTS doc in "
                                                                           + "/Indexes/Proxy (with "
                                                                           + "propose_instructions)."));
    }

    @Test
    void theIndexPlanOfAFeedingPipelineComesBeforeAConventionNameTheIndexAlsoHas() {
        plan("EventSource/User/Id", PlannedField.of("user.name", "keyword", "EventSource/User/Id"),
                PlannedField.of("actor.login", "keyword", "EventSource/User/Id"));

        // The plan's first field is one the index hasn't: its next is used, before the convention's user.name.
        assertThat(find(List.of(), Set.of("actor.login", "UserId"))).hasValueSatisfying(found -> {
            assertThat(found).isEqualTo(new Found("actor.login", Origin.PLAN, "Proxy indexing"));
            assertThat(found.from()).isEqualTo("the index plan of 'Proxy indexing'");
            assertThat(found.suggestion()).isNull();
        });
        // The AGENTS doc's line still comes first.
        rule("Initiator.Id");
        assertThat(find(List.of(), Set.of("actor.login", "Initiator.Id")))
                .hasValue(new Found("Initiator.Id", Origin.AGENTS, "/Indexes"));
    }

    @Test
    void anActionsWildcardPathIsLookedUpInThePlan() {
        plan("EventDetail/*/Action", PlannedField.of("event.action", "keyword", "EventDetail/Authenticate/Action"));

        assertThat(fields.find(SESSION, PROXY, InstructionRules.ACTION_FIELD, List.of(),
                Set.of("event.action")::contains)).hasValue(new Found("event.action", Origin.PLAN, "Proxy indexing"));
    }

    @Test
    void noPlanOrOneThatCantBeReadFallsBackToTheConvention() {
        Mockito.when(documentation.feedingPlans(PROXY)).thenThrow(new IllegalStateException("no"));

        assertThat(find(List.of(), Set.of("user.name"))).hasValueSatisfying(found -> {
            assertThat(found.origin()).isEqualTo(Origin.CONVENTION);
            assertThat(found.suggestion()).contains("`- user: user.name`", "the index's folder");
        });
    }

    @Test
    void theIndexsDocumentationIsReadLast() {
        Mockito.when(dataSourceAccess.documentation(PROXY)).thenReturn(Optional.of("""
                # Proxy logs
                Each request's time is `@timestamp`.
                The user who made the request is in `cs_username`.
                """));

        assertThat(find(List.of(), Set.of("@timestamp", "cs_username"))).hasValueSatisfying(found -> {
            assertThat(found.field()).isEqualTo("cs_username");
            assertThat(found.origin()).isEqualTo(Origin.DOCUMENTATION);
            assertThat(found.suggestion()).startsWith("To make cs_username the user field here, add `- user: "
                                                      + "cs_username`");
        });
        assertThat(find(List.of(), Set.of("@timestamp"))).isEmpty();
    }

    @Test
    void everyFeedingPipelinesPlanIsReadAndWhereTheyDifferEachPipelinesFieldIsGiven() {
        final DocRef first = new DocRef("Pipeline", "a-uuid", "A indexing");
        final DocRef second = new DocRef("Pipeline", "b-uuid", "B indexing");
        final Map<DocRef, List<PlannedField>> byPipeline = new LinkedHashMap<>();
        byPipeline.put(first, List.of(PlannedField.of("user.name", "keyword", "EventSource/User/Id")));
        byPipeline.put(second, List.of(PlannedField.of("UserId", "keyword", "EventSource/User/Id")));
        plans(byPipeline);

        assertThat(find(List.of(), Set.of("user.name", "UserId"))).hasValueSatisfying(found -> {
            assertThat(found.field()).isEqualTo("user.name");
            assertThat(found.varies()).isTrue();
            assertThat(found.fields()).containsExactly("user.name", "UserId");
            assertThat(found.byPipeline()).containsEntry("UserId", List.of("B indexing"));
            assertThat(found.from()).isEqualTo("the index plans, which differ by pipeline: user.name ('A "
                                               + "indexing'), UserId ('B indexing')");
        });
        // Only the fields the index has: B's alone.
        assertThat(find(List.of(), Set.of("UserId"))).hasValueSatisfying(found -> {
            assertThat(found.varies()).isFalse();
            assertThat(found.from()).isEqualTo("the index plan of 'B indexing'");
        });
    }

    @Test
    void pipelinesWhosePlansAgreeGiveOneField() {
        final Map<DocRef, List<PlannedField>> byPipeline = new LinkedHashMap<>();
        byPipeline.put(new DocRef("Pipeline", "a-uuid", "A indexing"),
                List.of(PlannedField.of("user.name", "keyword", "EventSource/User/Id")));
        byPipeline.put(new DocRef("Pipeline", "b-uuid", "B indexing"),
                List.of(PlannedField.of("user.name", "keyword", "EventSource/User/Id")));
        plans(byPipeline);

        assertThat(find(List.of(), Set.of("user.name"))).hasValueSatisfying(found -> {
            assertThat(found.varies()).isFalse();
            assertThat(found.fields()).containsExactly("user.name");
            assertThat(found.from()).isEqualTo("the index plan of 'A indexing'");
        });
    }

    @Test
    void theIndexDocumentationsFromColumnComesAfterThePlansAndBeforeTheConventions() {
        Mockito.when(documentation.find(Mockito.eq(SESSION), Mockito.eq(PROXY), Mockito.anyString()))
                .thenReturn(List.of(new DataSourceDocumentation.Documented(PROXY, PROXY, "its Documentation tab", """
                        | Index field | Description | From (event-logging path) |
                        | --- | --- | --- |
                        | `actor.login` | Who did it. | `EventSource/User/Id` |
                        """)));

        assertThat(find(List.of(), Set.of("actor.login", "user.name"))).hasValueSatisfying(found -> {
            assertThat(found).isEqualTo(new Found("actor.login", Origin.INDEX_DOCUMENTATION, null));
            assertThat(found.from()).isEqualTo("the From column of the index's documentation");
            assertThat(found.suggestion()).isNull();
        });
        // A plan still comes first.
        plan("EventSource/User/Id", PlannedField.of("user.name", "keyword", "EventSource/User/Id"));
        assertThat(new TimelineFields(agentInstructions, dataSourceAccess, Optional.of(knowledge),
                () -> documentation).find(SESSION, PROXY, InstructionRules.USER_FIELD, List.of(),
                Set.of("actor.login", "user.name")::contains)).map(Found::origin).hasValue(Origin.PLAN);
    }

    @Test
    void anIndexsPlansAreReadOnceForTheLookupsOfACall() {
        plan("EventSource/User/Id", PlannedField.of("user.name", "keyword", "EventSource/User/Id"),
                PlannedField.of("host.name", "keyword", "EventSource/Device/HostName"));

        assertThat(find(List.of(), Set.of("user.name", "host.name"))).isPresent();
        assertThat(fields.find(SESSION, PROXY, InstructionRules.HOST_FIELD, List.of(),
                Set.of("host.name")::contains)).map(Found::origin).hasValue(Origin.PLAN);

        Mockito.verify(documentation, Mockito.times(1)).feedingPlans(PROXY);
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

    // The plan of a pipeline feeding the index has these fields.
    private void plan(final String path, final PlannedField... planned) {
        plans(Map.of(new DocRef("Pipeline", "pipe-uuid", "Proxy indexing"), List.of(planned)));
    }

    // The plans of the pipelines feeding the index have these fields, in order.
    private void plans(final Map<DocRef, List<PlannedField>> byPipeline) {
        final Map<DocRef, List<FieldPlan>> plans = new LinkedHashMap<>();
        byPipeline.forEach((pipeline, planned) -> plans.put(pipeline, List.of(FieldPlan.of(FieldPlan.ELASTICSEARCH,
                "proxy", "@timestamp", planned))));
        Mockito.when(documentation.feedingPlans(PROXY)).thenReturn(new FeedingPlans(List.copyOf(plans.keySet()),
                plans));
    }

    private void rule(final String... names) {
        Mockito.when(agentInstructions.fieldsRuled(SESSION, PROXY)).thenReturn(Map.of(InstructionRules.USER_FIELD,
                new FieldRule(List.of(names), "/Indexes")));
    }
}
