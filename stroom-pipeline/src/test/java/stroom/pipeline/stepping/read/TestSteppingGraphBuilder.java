/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.pipeline.stepping.read;

import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.stepping.read.StagePlanner.PlannerElement;
import stroom.pipeline.stepping.read.SteppingGraphBuilder.Graph;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestSteppingGraphBuilder {

    private Map<String, Boolean> boundaryById(final Graph graph) {
        return graph.elements().stream()
                .collect(Collectors.toMap(PlannerElement::id, PlannerElement::atOrAboveRecordBoundary));
    }

    @Test
    void testLinearChain() {
        // Source -> parser -> xslt -> writer; Source is not captured (not steppable).
        final PipelineData data = new PipelineDataBuilder()
                .addElement(new PipelineElement("Source", "Source"))
                .addElement(new PipelineElement("parser", "CombinedParser"))
                .addElement(new PipelineElement("xslt", "XSLTFilter"))
                .addElement(new PipelineElement("writer", "XMLWriter"))
                .addLink("Source", "parser")
                .addLink("parser", "xslt")
                .addLink("xslt", "writer")
                .build();

        final Graph graph = SteppingGraphBuilder.build(data, Set.of("parser", "xslt", "writer"));

        assertThat(graph.parentsOf().get("parser")).isEmpty();
        assertThat(graph.parentsOf().get("xslt")).containsExactly("parser");
        assertThat(graph.parentsOf().get("writer")).containsExactly("xslt");
        // Only the parser (no steppable parent) is the record boundary.
        assertThat(boundaryById(graph)).containsEntry("parser", true)
                .containsEntry("xslt", false).containsEntry("writer", false);
    }

    @Test
    void testSkipsNonSteppableIntermediate() {
        // parser -> filter(non-steppable) -> xslt: xslt's steppable parent is the parser, not the filter.
        final PipelineData data = new PipelineDataBuilder()
                .addElement(new PipelineElement("Source", "Source"))
                .addElement(new PipelineElement("parser", "CombinedParser"))
                .addElement(new PipelineElement("filter", "SomeInvisibleFilter"))
                .addElement(new PipelineElement("xslt", "XSLTFilter"))
                .addLink("Source", "parser")
                .addLink("parser", "filter")
                .addLink("filter", "xslt")
                .build();

        // 'filter' is not in the captured set, so it is skipped over.
        final Graph graph = SteppingGraphBuilder.build(data, Set.of("parser", "xslt"));

        assertThat(graph.parentsOf().get("xslt")).containsExactly("parser");
        assertThat(graph.parentsOf().get("parser")).isEmpty();
    }

    @Test
    void testForkHasMultipleParents() {
        // parserA, parserB both feed a merging xslt.
        final PipelineData data = new PipelineDataBuilder()
                .addElement(new PipelineElement("Source", "Source"))
                .addElement(new PipelineElement("parserA", "CombinedParser"))
                .addElement(new PipelineElement("parserB", "CombinedParser"))
                .addElement(new PipelineElement("xslt", "XSLTFilter"))
                .addLink("Source", "parserA")
                .addLink("Source", "parserB")
                .addLink("parserA", "xslt")
                .addLink("parserB", "xslt")
                .build();

        final Graph graph = SteppingGraphBuilder.build(data, Set.of("parserA", "parserB", "xslt"));

        assertThat(graph.parentsOf().get("xslt")).containsExactlyInAnyOrder("parserA", "parserB");
        assertThat(boundaryById(graph)).containsEntry("parserA", true).containsEntry("parserB", true)
                .containsEntry("xslt", false);
    }
}
