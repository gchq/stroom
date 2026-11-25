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

import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.processing.MergedPipeline;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineLayer;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/// Which template a pipeline inherits, as facts: the template stated, a template unlike its predecessor's or its
/// siblings' flagged, and an XSLT of its own where its template supplies one flagged; nothing changed.
class TestPipelineInheritance {

    private static final DocRef PROD = new DocRef(PipelineDoc.TYPE, "prod", "Kafka Forwarding");
    private static final DocRef PRE_PROD = new DocRef(PipelineDoc.TYPE, "pre", "Kafka Forwarding (pre-prod)");
    private static final DocRef FOLDER = new DocRef(ExplorerConstants.FOLDER_TYPE, "f", "Vault");

    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final PipelineShapes shapes = Mockito.mock(PipelineShapes.class);
    private final PipelineInheritance inheritance = new PipelineInheritance(documentAccess, shapes);
    private final Map<String, PipelineDoc> pipelines = new HashMap<>();
    private final Map<String, List<PipelineLayer>> layers = new HashMap<>();
    private final List<ExplorerNode> inFolder = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Mockito.when(documentAccess.read(any(DocRef.class), eq(PipelineDoc.class))).thenAnswer(inv -> Optional
                .ofNullable(pipelines.get(((DocRef) inv.getArgument(0)).getUuid())));
        Mockito.when(documentAccess.parent(any())).thenReturn(Optional.of(FOLDER));
        Mockito.when(documentAccess.children(FOLDER)).thenReturn(inFolder);
        Mockito.when(documentAccess.folderPath(FOLDER)).thenReturn("/Vault");
        Mockito.when(shapes.shape(anyString())).thenAnswer(inv -> new PipelineShapes.Shape("forwarding", "kafka",
                List.of(), null, List.of(), List.of(), List.of(), Map.of()));
        Mockito.when(shapes.merged(anyString())).thenAnswer(inv -> MergedPipeline.of(layers.get(
                (String) inv.getArgument(0))));
        template(PROD, null);
        template(PRE_PROD, null);
    }

    @Test
    void aPipelineReparentedAwayFromItsPredecessorsTemplateIsFlaggedAsAFact() {
        pipeline("v1", "Vault CEF V1.0", PROD, null);
        pipeline("v2", "Vault CEF V1.1", PRE_PROD, null);

        final PipelineInheritance.Facts facts = inheritance.facts("v2", "v1", null).orElseThrow();

        assertThat(facts.template()).isEqualTo(PRE_PROD);
        assertThat(facts.flags()).containsExactly("'Vault CEF V1.1' inherits 'Kafka Forwarding (pre-prod)'; its "
                                                  + "predecessor 'Vault CEF V1.0' inherits 'Kafka Forwarding'");
        assertThat(facts.toMap()).containsEntry("inherits", MergedPipeline.docRef(PRE_PROD));
        assertThat(facts.said()).isEqualTo("'Vault CEF V1.1' inherits 'Kafka Forwarding (pre-prod)'; its predecessor "
                                           + "'Vault CEF V1.0' inherits 'Kafka Forwarding'");
        // The same template: nothing to flag.
        pipeline("v3", "Vault CEF V1.2", PROD, null);
        assertThat(inheritance.facts("v3", "v1", null).orElseThrow().flags()).isEmpty();
        assertThat(inheritance.facts("v3", "v1", null).orElseThrow().said()).isEqualTo(
                "'Vault CEF V1.2' inherits 'Kafka Forwarding'");
    }

    @Test
    void aTemplateNoSiblingOfItsKindInheritsIsFlagged() {
        pipeline("a", "Accounts CEF", PROD, null);
        pipeline("b", "Billing CEF", PROD, null);
        pipeline("v", "Vault CEF", PRE_PROD, null);
        node("a", "Accounts CEF");
        node("b", "Billing CEF");
        node("v", "Vault CEF");

        assertThat(inheritance.facts("v", null, null).orElseThrow().flags()).containsExactly(
                "'Vault CEF' inherits 'Kafka Forwarding (pre-prod)'; the other forwarding pipelines in folder "
                + "'/Vault' inherit 'Kafka Forwarding' ('Accounts CEF', 'Billing CEF')");
        assertThat(inheritance.facts("a", null, null).orElseThrow().flags()).as("one beside its own").isEmpty();

        // Siblings of another kind don't count.
        Mockito.when(shapes.shape("a")).thenReturn(new PipelineShapes.Shape("translation", null, List.of(), null,
                List.of(), List.of(), List.of(), Map.of()));
        Mockito.when(shapes.shape("b")).thenReturn(new PipelineShapes.Shape("translation", null, List.of(), null,
                List.of(), List.of(), List.of(), Map.of()));
        assertThat(inheritance.facts("v", null, null).orElseThrow().flags()).isEmpty();
    }

    @Test
    void anXsltOfItsOwnWhereItsTemplateSuppliesOneWontFollowTheTemplate() {
        template(PROD, "Decorate (LMDB)");
        pipeline("v", "Vault Events", PROD, "Decorate (my own)");

        assertThat(inheritance.facts("v", null, null).orElseThrow().flags()).containsExactly(
                "'Vault Events' sets its own XSLT 'Decorate (my own)' on 'decorationFilter', where its template "
                + "'Kafka Forwarding' supplies 'Decorate (LMDB)': it won't follow the template (a change to "
                + "'Decorate (LMDB)', or a template swapped in for this one, won't reach it)");
    }

    @Test
    void noTemplateAndAPipelineThatCantBeReadAreSaidPlainly() {
        pipeline("own", "Standalone CEF", null, null);

        final PipelineInheritance.Facts facts = inheritance.facts("own", null, null).orElseThrow();
        assertThat(facts.template()).isNull();
        assertThat(facts.said()).isEqualTo("'Standalone CEF' inherits no template");
        assertThat(facts.toMap()).containsEntry("inherits", null).doesNotContainKey("flags");
        assertThat(inheritance.facts("missing", null, null)).isEmpty();
    }

    // A template with a decoration XSLT if given.
    private void template(final DocRef ref, final String decoration) {
        final PipelineDataBuilder data = new PipelineDataBuilder()
                .addElement(new PipelineElement("decorationFilter", "XSLTFilter"));
        if (decoration != null) {
            data.addProperty(xslt(decoration));
        }
        pipelines.put(ref.getUuid(), PipelineDoc.builder().uuid(ref.getUuid()).name(ref.getName())
                .pipelineData(data.build()).build());
        layers.put(ref.getUuid(), List.of(new PipelineLayer(ref, data.build())));
    }

    // A pipeline inheriting the template given (or none), setting its own decoration XSLT if given.
    private void pipeline(final String uuid, final String name, final DocRef parent, final String decoration) {
        final PipelineDataBuilder data = new PipelineDataBuilder();
        if (decoration != null) {
            data.addProperty(xslt(decoration));
        }
        final DocRef ref = new DocRef(PipelineDoc.TYPE, uuid, name);
        pipelines.put(uuid, PipelineDoc.builder().uuid(uuid).name(name).parentPipeline(parent)
                .pipelineData(data.build()).build());
        final List<PipelineLayer> chain = new ArrayList<>();
        if (parent != null) {
            chain.addAll(layers.get(parent.getUuid()));
        }
        chain.add(new PipelineLayer(ref, data.build()));
        layers.put(uuid, chain);
    }

    private void node(final String uuid, final String name) {
        inFolder.add(ExplorerNode.builder().docRef(new DocRef(PipelineDoc.TYPE, uuid, name)).build());
    }

    private static PipelineProperty xslt(final String name) {
        return new PipelineProperty("decorationFilter", "xslt", new PipelinePropertyValue(new DocRef(XsltDoc.TYPE,
                name, name)));
    }
}
