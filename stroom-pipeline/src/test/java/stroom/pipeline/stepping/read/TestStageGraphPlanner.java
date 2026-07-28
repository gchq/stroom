package stroom.pipeline.stepping.read;

import stroom.pipeline.factory.PipelineFactory.MidPipelineScope;
import stroom.pipeline.stepping.read.StageGraphPlanner.Stage;
import stroom.pipeline.stepping.read.StageGraphPlanner.StageGraph;
import stroom.pipeline.stepping.read.StagePlanner.PlannerElement;
import stroom.pipeline.stepping.read.SteppingGraphBuilder.Graph;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where a pipeline can be cut into independently runnable stages, and - just as important - where it cannot.
 * A cut is only sound where the upstream element's captured output can be replayed into the next element, so
 * these tests are mostly about the shape of the real pipelines: an XML span that splits, and text-fed ends
 * that must not.
 */
class TestStageGraphPlanner {

    /**
     * A straight chain, in order. Parents are the previous element.
     */
    private static Graph chain(final String... ids) {
        final Map<String, List<String>> parentsOf = new LinkedHashMap<>();
        for (int i = 1; i < ids.length; i++) {
            parentsOf.put(ids[i], List.of(ids[i - 1]));
        }
        return new Graph(
                List.of(ids).stream().map(id -> new PlannerElement(id, false)).toList(),
                parentsOf);
    }

    private static List<String> startsOf(final StageGraph graph) {
        return graph.stages().stream().map(Stage::startElementId).toList();
    }

    @Test
    void testTheUsualPipelineSplitsAtTheEventBoundaries() {
        // The real shape: the parser and the two XML filters produce events, so cuts are possible below each
        // of them; the writer produces text, so its appenders travel with it.
        final Graph graph = chain("combinedParser", "translationFilter", "schemaFilter", "xmlWriter",
                "streamAppender");
        final StageGraph plan = StageGraphPlanner.plan(graph,
                Set.of("combinedParser", "translationFilter", "schemaFilter"));

        assertThat(plan.decomposed()).isTrue();
        assertThat(startsOf(plan)).containsExactly(
                "combinedParser", "translationFilter", "schemaFilter", "xmlWriter");

        // The head reads the raw stream; everything else is fed from the element above it.
        assertThat(plan.stages().getFirst().isHead()).isTrue();
        assertThat(plan.stages().get(1).feedElementId()).isEqualTo("combinedParser");
        assertThat(plan.stages().get(2).feedElementId()).isEqualTo("translationFilter");
        assertThat(plan.stages().get(3).feedElementId()).isEqualTo("schemaFilter");

        // The text tail cannot be cut, so the final stage carries the appender with it.
        assertThat(plan.stages().getLast().elementIds()).containsExactly("xmlWriter", "streamAppender");
        assertThat(plan.stages().getLast().scope()).isEqualTo(MidPipelineScope.ELEMENT_AND_DESCENDANTS);
    }

    @Test
    void testEveryStageButTheLastRunsOnlyItsOwnElement() {
        // Otherwise a stage would run - and capture - the elements the next stage owns, writing the same
        // chunks twice.
        final Graph graph = chain("parser", "xslt", "writer");
        final StageGraph plan = StageGraphPlanner.plan(graph, Set.of("parser", "xslt"));

        assertThat(plan.stages()).hasSize(3);
        assertThat(plan.stages().get(0).scope()).isEqualTo(MidPipelineScope.ELEMENT_ONLY);
        assertThat(plan.stages().get(1).scope()).isEqualTo(MidPipelineScope.ELEMENT_ONLY);
        assertThat(plan.stages().get(2).scope()).isEqualTo(MidPipelineScope.ELEMENT_AND_DESCENDANTS);
    }

    @Test
    void testTheHeadStageKeepsItsTextFedElements() {
        // Readers store text, so the parser cannot be cut from the readers above it - they run as one head
        // stage against the raw stream.
        final Graph graph = chain("bomRemovalFilterInput", "findReplaceFilter", "combinedParser",
                "translationFilter");
        final StageGraph plan = StageGraphPlanner.plan(graph, Set.of("combinedParser"));

        assertThat(plan.decomposed()).isTrue();
        assertThat(plan.stages()).hasSize(2);
        assertThat(plan.stages().getFirst().elementIds())
                .containsExactly("bomRemovalFilterInput", "findReplaceFilter", "combinedParser");
        assertThat(plan.stages().getFirst().isHead()).isTrue();
        assertThat(plan.stages().getLast().startElementId()).isEqualTo("translationFilter");
    }

    @Test
    void testTheHeadMayHoldSeveralElementsAndStillBeFollowed() {
        // The head is exempt from the one-element rule that applies to mid-pipeline stages: it is built from
        // Source, and stopping after its last element is the same build either way.
        final Graph graph = chain("reader", "parser", "xslt", "writer");
        // Nothing replayable until the parser, so the head is reader+parser - and it is followed by more.
        final StageGraph plan = StageGraphPlanner.plan(graph, Set.of("parser"));

        assertThat(plan.decomposed()).isTrue();
        assertThat(plan.stages().getFirst().elementIds()).containsExactly("reader", "parser");
        assertThat(plan.stages().getFirst().scope())
                .as("the head runs to its own end and stops")
                .isEqualTo(MidPipelineScope.ELEMENT_ONLY);
    }

    @Test
    void testNothingReplayableMeansOneWholePipelineStage() {
        final Graph graph = chain("reader", "parser", "writer");
        final StageGraph plan = StageGraphPlanner.plan(graph, Set.of());

        assertThat(plan.decomposed()).isFalse();
        assertThat(plan.stages()).hasSize(1);
        assertThat(plan.stages().getFirst().isHead()).isTrue();
        assertThat(plan.stages().getFirst().elementIds()).containsExactly("reader", "parser", "writer");
    }

    @Test
    void testAForkIsNotDecomposed() {
        // Two children means one element's output feeds two stages. Falls back to the single stage, which is
        // exactly today's sweep - the same fallback the reprocess planner already makes for forks.
        final Graph graph = new Graph(
                List.of(new PlannerElement("parser", false),
                        new PlannerElement("xsltA", false),
                        new PlannerElement("xsltB", false)),
                Map.of("xsltA", List.of("parser"), "xsltB", List.of("parser")));

        final StageGraph plan = StageGraphPlanner.plan(graph, Set.of("parser"));

        assertThat(plan.decomposed()).isFalse();
        assertThat(plan.stages()).hasSize(1);
    }

    @Test
    void testAJoinIsNotDecomposed() {
        final Graph graph = new Graph(
                List.of(new PlannerElement("a", false),
                        new PlannerElement("b", false),
                        new PlannerElement("merge", false)),
                Map.of("merge", List.of("a", "b")));

        assertThat(StageGraphPlanner.plan(graph, Set.of("a", "b")).decomposed()).isFalse();
    }

    @Test
    void testAnElementOffTheChainIsNotDecomposed() {
        // A second head is another branch we have not modelled; better to sweep than to run part of it.
        final Graph graph = new Graph(
                List.of(new PlannerElement("parser", false),
                        new PlannerElement("xslt", false),
                        new PlannerElement("orphan", false)),
                Map.of("xslt", List.of("parser")));

        assertThat(StageGraphPlanner.plan(graph, Set.of("parser")).decomposed()).isFalse();
    }

    @Test
    void testAnEmptyGraphIsNotDecomposed() {
        final StageGraph plan = StageGraphPlanner.plan(new Graph(List.of(), Map.of()), Set.of());

        assertThat(plan.decomposed()).isFalse();
        assertThat(plan.stages()).hasSize(1);
    }

    @Test
    void testEveryElementLandsInExactlyOneStage() {
        // The invariant that stops an element being captured twice under two different stages.
        final Graph graph = chain("parser", "xslt", "schema", "writer", "appender");
        final StageGraph plan = StageGraphPlanner.plan(graph, Set.of("parser", "xslt", "schema"));

        final List<String> assigned = plan.stages().stream().flatMap(s -> s.elementIds().stream()).toList();
        assertThat(assigned)
                .containsExactly("parser", "xslt", "schema", "writer", "appender")
                .doesNotHaveDuplicates();
    }
}
