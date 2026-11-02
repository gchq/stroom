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

import stroom.ai.impl.AiConfig;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.pipeline.PipelineService;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineLayer;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.pipeline.shared.data.PipelineReference;

import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

/// Builds pipeline layers for the tests, as Stroom stores them, and a [PipelineShapes] that reads them.
final class PipelineLayers {

    private final PipelineDataBuilder builder = new PipelineDataBuilder();
    private final DocRef source;

    private PipelineLayers(final DocRef source) {
        this.source = source;
    }

    /// @param uuid The UUID of the pipeline the layer is.
    /// @return A builder for its layer.
    static PipelineLayers layer(final String uuid) {
        return new PipelineLayers(new DocRef(PipelineDoc.TYPE, uuid, uuid));
    }

    /// @param ids Element ids and types, in pairs.
    /// @return This builder, with the elements added and linked in the order given.
    PipelineLayers chain(final String... ids) {
        String previous = null;
        for (int i = 0; i < ids.length; i += 2) {
            builder.addElement(new PipelineElement(ids[i], ids[i + 1]));
            if (previous != null) {
                builder.addLink(previous, ids[i]);
            }
            previous = ids[i];
        }
        return this;
    }

    /// @param id   An element's id.
    /// @param type Its type.
    /// @return This builder, with the element added.
    PipelineLayers element(final String id, final String type) {
        builder.addElement(new PipelineElement(id, type));
        return this;
    }

    /// @param from An element's id.
    /// @param to   Another's.
    /// @return This builder, with the link added.
    PipelineLayers link(final String from, final String to) {
        builder.addLink(from, to);
        return this;
    }

    /// @param element An element's id.
    /// @param name    A property's name.
    /// @param value   Its string value.
    /// @return This builder, with the property set.
    PipelineLayers property(final String element, final String name, final String value) {
        builder.addProperty(new PipelineProperty(element, name, new PipelinePropertyValue(value)));
        return this;
    }

    /// @param element An element's id.
    /// @param name    A property's name.
    /// @param doc     The document it names.
    /// @return This builder, with the property set.
    PipelineLayers property(final String element, final String name, final DocRef doc) {
        builder.addProperty(new PipelineProperty(element, name, new PipelinePropertyValue(doc)));
        return this;
    }

    /// @param element  The element the reference is on.
    /// @param feed     The reference feed.
    /// @param pipeline The loader pipeline.
    /// @return This builder, with the reference added.
    PipelineLayers reference(final String element, final DocRef feed, final DocRef pipeline) {
        builder.addPipelineReference(new PipelineReference(element, "reference", pipeline, feed, "Reference"));
        return this;
    }

    /// @return The layer's data.
    PipelineData data() {
        return builder.build();
    }

    /// @return The layer.
    PipelineLayer build() {
        return new PipelineLayer(source, builder.build());
    }

    /// @param layers Layers, root first.
    /// @return Them as a list.
    static List<PipelineLayer> of(final PipelineLayers... layers) {
        final List<PipelineLayer> list = new ArrayList<>();
        for (final PipelineLayers layer : layers) {
            list.add(layer.build());
        }
        return list;
    }

    /// @param pipelineService Gives each pipeline's layers.
    /// @param aiConfig        Gives the stage markers.
    /// @return Shapes that read pipelines from the service.
    static PipelineShapes shapes(final PipelineService pipelineService, final AiConfig aiConfig) {
        return shapes(pipelineService, aiConfig, Mockito.mock(DocumentAccess.class), Mockito.mock(DocFinder.class));
    }

    /// @param pipelineService Gives each pipeline's layers.
    /// @param aiConfig        Gives the stage markers.
    /// @param documentAccess  Reads the pipelines' XSLTs.
    /// @param docFinder       Finds the XSLTs they import, by name.
    /// @return Shapes that read pipelines from the service, and their XSLTs from the document access.
    static PipelineShapes shapes(final PipelineService pipelineService,
                                 final AiConfig aiConfig,
                                 final DocumentAccess documentAccess,
                                 final DocFinder docFinder) {
        return new PipelineShapes(() -> pipelineService, () -> aiConfig, () -> documentAccess, () -> docFinder);
    }

    /// @return A pipeline service mock.
    static PipelineService service() {
        return Mockito.mock(PipelineService.class);
    }

    /// @param service A pipeline service mock.
    /// @param uuid    A pipeline's UUID.
    /// @param layers  Its layers.
    static void given(final PipelineService service, final String uuid, final List<PipelineLayer> layers) {
        Mockito.when(service.fetchPipelineLayers(new DocRef(PipelineDoc.TYPE, uuid))).thenReturn(layers);
    }
}
