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

import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.PlannedField;
import stroom.mcp.impl.tools.FeedingPlanMerges.Feeder;
import stroom.mcp.impl.tools.FeedingPlanMerges.Merged;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// An index template for an index fed by several pipelines merges every feeding pipeline's plan; a type conflict
/// between plans is refused, naming both pipelines and the field.
class TestFeedingPlanMerges {

    private static final FieldPlan OWN = plan(PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
            PlannedField.of("user.name", "keyword", "EventSource/User/Id"));

    @Test
    void anotherPipelinesFieldsAreAddedAfterTheOwnAndAgreeingOnesKeptOnce() {
        final Merged merged = FeedingPlanMerges.merge(OWN, "Events indexing", List.of(new Feeder("Mail indexing",
                "p2", plan(PlannedField.of("user.name", "Keyword", "EventDetail/Send/Source/User/Id"),
                PlannedField.of("email.subject", "text", "EventDetail/Send/Subject")))), "acme");

        assertThat(merged.plan().fields()).extracting(PlannedField::name)
                .containsExactly("@timestamp", "user.name", "email.subject");
        // The own plan's source for an agreed field.
        assertThat(merged.plan().fields().get(1).source()).isEqualTo("EventSource/User/Id");
        assertThat(merged.mergedFrom()).containsExactly("Mail indexing");
        assertThat(merged.plan().indexName()).isEqualTo(OWN.indexName());
    }

    @Test
    void noOtherPipelineLeavesThePlanAsItIs() {
        assertThat(FeedingPlanMerges.merge(OWN, "Events indexing", List.of(), "acme").plan()).isSameAs(OWN);
        assertThat(FeedingPlanMerges.merge(OWN, "Events indexing", null, "acme").mergedFrom()).isEmpty();
    }

    @Test
    void aTypeConflictIsRefusedNamingTheFieldAndBothPipelines() {
        assertThatThrownBy(() -> FeedingPlanMerges.merge(OWN, "Events indexing", List.of(new Feeder(
                "Mail indexing", "p2", plan(PlannedField.of("user.name", "text", "EventSource/User/Id")))), "acme"))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("Field 'user.name' is keyword in the index plan of 'Events indexing' but "
                                      + "text in the plan of 'Mail indexing', and both write to index 'acme'");
        // Between two other pipelines: those two are named.
        assertThatThrownBy(() -> FeedingPlanMerges.merge(OWN, "Events indexing", List.of(
                new Feeder("Mail indexing", "p2", plan(PlannedField.of("bytes", "long", "EventDetail/Size"))),
                new Feeder("Proxy indexing", "p3", plan(PlannedField.of("bytes", "keyword",
                        "EventDetail/Size")))), "acme"))
                .hasMessageContaining("'bytes' is long in the index plan of 'Mail indexing' but keyword in the plan "
                                      + "of 'Proxy indexing'");
    }

    @Test
    void theFeedingPipelinesAreReadWithTheirKeptPlansLeavingThePipelineItself() {
        final IndexSurveys surveys = Mockito.mock(IndexSurveys.class);
        Mockito.when(surveys.feedingPipelines(Mockito.any(), Mockito.eq("acme"))).thenReturn(List.of(
                Map.of("uuid", "p1", "name", "Events indexing"),
                Map.of("uuid", "p2", "name", "Mail indexing", "plan", plan(PlannedField.of("email.subject", "text",
                        "EventDetail/Send/Subject")).toJson()),
                Map.of("uuid", "p3", "name", "Unplanned indexing")));

        final Merged merged = new FeedingPlanMerges(surveys).forIndex("p1", OWN, "acme");

        assertThat(merged.mergedFrom()).containsExactly("Mail indexing");
        assertThat(merged.plan().fields()).extracting(PlannedField::name).contains("email.subject");
        // Unread: the pipeline's own plan.
        Mockito.when(surveys.feedingPipelines(Mockito.any(), Mockito.eq("acme"))).thenThrow(
                new IllegalStateException("no"));
        assertThat(new FeedingPlanMerges(surveys).forIndex("p1", OWN, "acme").plan()).isSameAs(OWN);
    }

    private static FieldPlan plan(final PlannedField... fields) {
        return FieldPlan.of(FieldPlan.ELASTICSEARCH, "acme", "@timestamp", List.of(fields));
    }
}
