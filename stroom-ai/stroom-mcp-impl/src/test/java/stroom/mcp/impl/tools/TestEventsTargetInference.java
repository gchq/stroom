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
import stroom.ai.impl.tools.InstructionRules;
import stroom.ai.impl.tools.InstructionRules.RequiredImport;
import stroom.ai.impl.tools.validation.HeldSchemas;
import stroom.docref.DocRef;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.xmlschema.shared.XmlSchemaDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestEventsTargetInference {

    private static final String FORK_NS = "file://xml/schema/test-fork/events";
    private static final String WRITES_FORK = """
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="3.0">
              <xsl:template name="source"><EventSource xmlns="%s"><Generator>g</Generator></EventSource>
              </xsl:template>
            </xsl:stylesheet>""".formatted(FORK_NS);

    private final HeldSchemas heldSchemas = Mockito.mock(HeldSchemas.class);
    private final RequiredImports requiredImports = Mockito.mock(RequiredImports.class);
    private final PipelineTemplates pipelineTemplates = Mockito.mock(PipelineTemplates.class);
    private final PipelineIndex pipelineIndex = Mockito.mock(PipelineIndex.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final EventsTargetInference inference = new EventsTargetInference(heldSchemas, () -> requiredImports,
            () -> pipelineTemplates, () -> pipelineIndex, documentAccess);

    @Test
    void withNoForkHeldNothingIsInferred() {
        assertThat(inference.infer(null, "b1")).isEmpty();
        Mockito.verifyNoInteractions(requiredImports, pipelineTemplates);
    }

    @Test
    void aRequiredImportThatWritesTheForkSettlesIt() {
        holdingFork();
        Mockito.when(requiredImports.requiredFor(null, InstructionRules.TRANSLATION, "b1", List.of()))
                .thenReturn(List.of(new RequiredImport("Common-Fields", "/")));
        Mockito.when(requiredImports.named("Common-Fields")).thenReturn(Optional.of(xslt("Common-Fields",
                WRITES_FORK)));

        assertThat(inference.infer(null, "b1")).hasValueSatisfying(inferred -> {
            assertThat(inferred.namespace()).isEqualTo(FORK_NS);
            assertThat(inferred.why()).contains("'Common-Fields'");
        });
    }

    @Test
    void anXsltSetOnTheTemplateItsParentSettlesItWhereNoImportDoes() {
        holdingFork();
        Mockito.when(pipelineTemplates.find(InstructionRules.TRANSLATION))
                .thenReturn(Map.of("candidates", List.of(Map.of("uuid", "child"))));
        final DocRef ref = new DocRef(XsltDoc.TYPE, "x1", "Fork-Header");
        Mockito.when(pipelineIndex.read()).thenReturn(Map.of(
                "child", new PipelineIndex.Entry("child", "Events Template", "/T", "parent", null),
                "parent", new PipelineIndex.Entry("parent", "Base Template", "/T", null, settingXslt(ref))));
        Mockito.when(documentAccess.read(ref, XsltDoc.class)).thenReturn(Optional.of(xslt("Fork-Header",
                WRITES_FORK)));

        assertThat(inference.infer(null, "b1")).hasValueSatisfying(inferred -> {
            assertThat(inferred.namespace()).isEqualTo(FORK_NS);
            assertThat(inferred.why()).contains("'Fork-Header'").contains("'Base Template'");
        });
    }

    @Test
    void anImportWritingEventLoggingSettlesItToo() {
        holdingFork();
        Mockito.when(requiredImports.requiredFor(null, InstructionRules.TRANSLATION, "b1", List.of()))
                .thenReturn(List.of(new RequiredImport("Common-Fields", "/")));
        Mockito.when(requiredImports.named("Common-Fields")).thenReturn(Optional.of(xslt("Common-Fields",
                WRITES_FORK.replace(FORK_NS, "event-logging:3"))));

        assertThat(inference.infer(null, "b1")).map(EventsTargetInference.Inferred::namespace)
                .contains("event-logging:3");
    }

    @Test
    void nothingThatWritesEventsLeavesItToTheUser() {
        holdingFork();
        Mockito.when(pipelineTemplates.find(InstructionRules.TRANSLATION)).thenReturn(Map.of("candidates",
                List.of()));

        assertThat(inference.infer(null, "b1")).isEmpty();
    }

    private void holdingFork() {
        Mockito.when(heldSchemas.forks()).thenReturn(List.of(XmlSchemaDoc.builder().uuid("fork-1").name("Fork")
                .systemId("file://test-fork-events-v1.2.0.xsd").namespaceURI(FORK_NS).schemaGroup("EVENTS")
                .build()));
    }

    private static XsltDoc xslt(final String name, final String data) {
        return XsltDoc.builder().uuid(name).name(name).data(data).build();
    }

    private static PipelineData settingXslt(final DocRef ref) {
        final PipelineDataBuilder builder = new PipelineDataBuilder();
        builder.addElement(new PipelineElement("xsltFilter", "XSLTFilter"));
        builder.addProperty(new PipelineProperty("xsltFilter", "xslt", new PipelinePropertyValue(ref)));
        return builder.build();
    }
}
