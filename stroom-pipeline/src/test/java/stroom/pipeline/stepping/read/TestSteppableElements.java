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

package stroom.pipeline.stepping.read;

import stroom.pipeline.factory.ElementRegistry;
import stroom.pipeline.factory.ElementRegistryFactory;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestSteppableElements {

    private static final String SOURCE = "Source";
    private static final String PARSER = "CombinedParser";
    private static final String XSLT = "XSLTFilter";
    private static final String WRITER = "XMLWriter";
    private static final String INVISIBLE = "InvisibleFilter";

    private SteppableElements steppableElements;

    @BeforeEach
    void setUp() {
        final ElementRegistry registry = Mockito.mock(ElementRegistry.class);
        Mockito.when(registry.getElementType(SOURCE)).thenReturn(type(SOURCE, PipelineElementType.ROLE_SOURCE));
        Mockito.when(registry.getElementType(PARSER)).thenReturn(type(PARSER,
                PipelineElementType.ROLE_PARSER, PipelineElementType.VISABILITY_STEPPING));
        Mockito.when(registry.getElementType(XSLT)).thenReturn(type(XSLT, PipelineElementType.VISABILITY_STEPPING));
        Mockito.when(registry.getElementType(WRITER)).thenReturn(type(WRITER,
                PipelineElementType.VISABILITY_STEPPING));
        Mockito.when(registry.getElementType(INVISIBLE)).thenReturn(type(INVISIBLE, PipelineElementType.ROLE_TARGET));
        final ElementRegistryFactory registryFactory = () -> registry;
        steppableElements = new SteppableElements(registryFactory);
    }

    @Test
    void linkedElementsAreSteppable() {
        final PipelineData data = new PipelineDataBuilder()
                .addElement(new PipelineElement("Source", SOURCE))
                .addElement(new PipelineElement("parser", PARSER))
                .addElement(new PipelineElement("xslt", XSLT))
                .addElement(new PipelineElement("writer", WRITER))
                .addLink("Source", "parser")
                .addLink("parser", "xslt")
                .addLink("xslt", "writer")
                .build();

        assertThat(steppableElements.in(data)).containsExactly("parser", "xslt", "writer");
        assertThat(steppableElements.boundaryIn(data)).containsExactly("parser");
    }

    @Test
    void anElementNothingLinksToIsNotSteppable() {
        // gh-5986: an unlinked XSLT never runs, so it must not count as a (record-boundary) steppable element.
        final PipelineData data = new PipelineDataBuilder()
                .addElement(new PipelineElement("Source", SOURCE))
                .addElement(new PipelineElement("parser", PARSER))
                .addElement(new PipelineElement("xslt", XSLT))
                .addElement(new PipelineElement("orphan", XSLT))
                .addLink("Source", "parser")
                .addLink("parser", "xslt")
                .build();

        assertThat(steppableElements.in(data)).containsExactly("parser", "xslt");
    }

    @Test
    void anElementLinkedOnlyFromAnOrphanIsNotSteppable() {
        // The orphan has a child, so the child has a parent, but neither is below Source.
        final PipelineData data = new PipelineDataBuilder()
                .addElement(new PipelineElement("Source", SOURCE))
                .addElement(new PipelineElement("parser", PARSER))
                .addElement(new PipelineElement("orphan", XSLT))
                .addElement(new PipelineElement("orphanWriter", WRITER))
                .addLink("Source", "parser")
                .addLink("orphan", "orphanWriter")
                .build();

        assertThat(steppableElements.in(data)).containsExactly("parser");
    }

    @Test
    void anUnlinkedParserIsNotABoundary() {
        final PipelineData data = new PipelineDataBuilder()
                .addElement(new PipelineElement("Source", SOURCE))
                .addElement(new PipelineElement("parser", PARSER))
                .addElement(new PipelineElement("spareParser", PARSER))
                .addLink("Source", "parser")
                .build();

        assertThat(steppableElements.boundaryIn(data)).containsExactly("parser");
    }

    @Test
    void anElementBelowANonSteppableElementIsSteppable() {
        final PipelineData data = new PipelineDataBuilder()
                .addElement(new PipelineElement("Source", SOURCE))
                .addElement(new PipelineElement("parser", PARSER))
                .addElement(new PipelineElement("invisible", INVISIBLE))
                .addElement(new PipelineElement("xslt", XSLT))
                .addLink("Source", "parser")
                .addLink("parser", "invisible")
                .addLink("invisible", "xslt")
                .build();

        assertThat(steppableElements.in(data)).containsExactly("parser", "xslt");
    }

    @Test
    void aLinkCycleDoesNotStopTheWalk() {
        final PipelineData data = new PipelineDataBuilder()
                .addElement(new PipelineElement("Source", SOURCE))
                .addElement(new PipelineElement("parser", PARSER))
                .addElement(new PipelineElement("xslt", XSLT))
                .addLink("Source", "parser")
                .addLink("parser", "xslt")
                .addLink("xslt", "parser")
                .build();

        assertThat(steppableElements.in(data)).containsExactly("parser", "xslt");
    }

    @Test
    void aPipelineWithNoLinksHasNoSteppableElements() {
        final PipelineData data = new PipelineDataBuilder()
                .addElement(new PipelineElement("Source", SOURCE))
                .addElement(new PipelineElement("parser", PARSER))
                .build();

        assertThat(steppableElements.in(data)).isEqualTo(Set.of());
        assertThat(steppableElements.boundaryIn(data)).isEqualTo(Set.of());
    }

    private static PipelineElementType type(final String typeName, final String... roles) {
        return new PipelineElementType(typeName, typeName, null, roles, null);
    }
}
