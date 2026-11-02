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

import stroom.ai.impl.tools.ErrorTriage;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

class TestUnvalidatedOutput {

    private static final String OUTPUT = "<records xmlns=\"records:2\"><record><entry/></record></records>";

    private final SteppingWorld world = new SteppingWorld();

    @Test
    void outputASchemaFilterChecksIsLeftToIt() {
        // The world's template has a schema filter after its translation: its errors already block a clean step.
        world.outputFailsItsSchema();

        final UnvalidatedOutput check = world.stepping().unvalidatedOutput(world.stepping().load(SteppingWorld.UUID));

        assertThat(check).isSameAs(UnvalidatedOutput.NONE);
        assertThat(check.markers(OUTPUT, "7:0")).isEmpty();
        Mockito.verify(world.outputSchemas, Mockito.never()).check(any());
    }

    @Test
    void outputNoSchemaFilterChecksIsCheckedAsAnErrorOfTheOutputElement() {
        world.withoutSchemaFilter();
        world.outputFailsItsSchema();

        final List<ErrorTriage.Marker> markers = world.stepping()
                .unvalidatedOutput(world.stepping().load(SteppingWorld.UUID))
                .markers(OUTPUT, "7:0");

        assertThat(markers).singleElement().satisfies(marker -> {
            assertThat(marker.severity()).isEqualTo("ERROR");
            assertThat(marker.element()).isEqualTo("translationFilter");
            assertThat(marker.record()).isEqualTo("7:0");
            assertThat(marker.location()).isEqualTo(Map.of("line", 2));
            assertThat(marker.message())
                    .startsWith("Invalid content was found starting with element 'entry'")
                    .contains("file://records-v2.0.xsd at /records/record/entry")
                    .contains(UnvalidatedOutput.NOT_CHECKED)
                    .contains("IndexingFilter");
        });
        // The bundled rules say it blocks, and why.
        assertThat(ErrorTriage.bundled().classify("ERROR", "translationFilter", true, markers.getFirst().message())
                .classification()).isEqualTo(ErrorTriage.BLOCKING);
    }

    @Test
    void validOutputHasNoMarker() {
        world.withoutSchemaFilter();

        assertThat(world.stepping().unvalidatedOutput(world.stepping().load(SteppingWorld.UUID))
                .markers(OUTPUT, "7:0")).isEmpty();
    }

    @Test
    void aSchemaFilterBeforeTheOutputElementDoesNotCheckIt() {
        world.pipeline(SteppingWorld.TEMPLATE, "Indexing", null, new PipelineDataBuilder()
                .addElement(new PipelineElement("dsParser", "DSParser"))
                .addElement(new PipelineElement("schemaFilter", "SchemaFilter"))
                .addElement(new PipelineElement("translationFilter", "XSLTFilter"))
                .addElement(new PipelineElement("indexingFilter", "IndexingFilter"))
                .addLink("dsParser", "schemaFilter")
                .addLink("schemaFilter", "translationFilter")
                .addLink("translationFilter", "indexingFilter"));
        world.outputFailsItsSchema();

        assertThat(world.stepping().unvalidatedOutput(world.stepping().load(SteppingWorld.UUID))
                .markers(OUTPUT, "7:0")).hasSize(1);
    }
}
