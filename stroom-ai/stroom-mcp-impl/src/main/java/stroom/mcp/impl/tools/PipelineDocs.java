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
import stroom.ai.impl.tools.ToolRefusal;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineProperties;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// A pipeline document read and saved as the user, and its own properties changed (the Stroom MCP server's
/// `tools/pipeline_writes.py` `_set_property`, and the reads and writes of a pipeline its processing tools make).
///
/// Only the pipeline's own layer is changed: the properties it adds over its templates.
class PipelineDocs {

    private final DocumentAccess documentAccess;

    /// @param documentAccess Reads and writes pipelines as the user.
    @Inject
    PipelineDocs(final DocumentAccess documentAccess) {
        this.documentAccess = documentAccess;
    }

    /// @param uuid A pipeline's UUID.
    /// @return It, as the user reads it.
    /// @throws ToolRefusal if it doesn't exist, or the user may not view it.
    PipelineDoc read(final String uuid) {
        return documentAccess.read(new DocRef(PipelineDoc.TYPE, uuid), PipelineDoc.class)
                .orElseThrow(() -> new ToolRefusal("Pipeline " + uuid + " can't be read: it doesn't exist, or you "
                                                   + "don't have permission to view it"));
    }

    /// @param doc A pipeline, changed.
    /// @return It, saved as the user (they need Edit on it).
    /// @throws ToolRefusal if Stroom has no pipeline store.
    @SuppressWarnings("unchecked")
    PipelineDoc write(final PipelineDoc doc) {
        final DocumentActionHandler<PipelineDoc> handler = documentAccess.handler(PipelineDoc.TYPE)
                .orElseThrow(() -> new ToolRefusal("Stroom has no Pipeline store"));
        return handler.writeDocument(doc);
    }

    /// @param doc A pipeline.
    /// @return The properties its own layer adds (set over its templates').
    static List<PipelineProperty> addedProperties(final PipelineDoc doc) {
        return NullSafe.list(NullSafe.get(doc.getPipelineData(), PipelineData::getAddedProperties));
    }

    /// @param doc   A pipeline.
    /// @param added The properties its own layer is to add, in place of those it adds now.
    /// @return The pipeline with them; its elements, links, references and removed properties as they were.
    static PipelineDoc withAddedProperties(final PipelineDoc doc, final List<PipelineProperty> added) {
        final PipelineData data = doc.getPipelineData();
        final List<PipelineProperty> removed = NullSafe.list(NullSafe.get(data, PipelineData::getRemovedProperties));
        final PipelineData changed = new PipelineData(
                NullSafe.get(data, PipelineData::getElements),
                new PipelineProperties(new ArrayList<>(added), new ArrayList<>(removed)),
                NullSafe.get(data, PipelineData::getPipelineReferences),
                NullSafe.get(data, PipelineData::getLinks));
        return doc.copy().pipelineData(changed).build();
    }

    /// Sets one of the pipeline's own properties, replacing any value it set before (`_set_property`).
    ///
    /// @param doc     A pipeline.
    /// @param element The element's id.
    /// @param name    The property's name.
    /// @param value   Its value.
    /// @return The pipeline with the property set, last among those it adds.
    static PipelineDoc withProperty(final PipelineDoc doc,
                                    final String element,
                                    final String name,
                                    final PipelinePropertyValue value) {
        final List<PipelineProperty> added = new ArrayList<>(addedProperties(doc).stream()
                .filter(property -> !isProperty(property, element, name))
                .toList());
        added.add(new PipelineProperty(element, name, value));
        return withAddedProperties(doc, added);
    }

    /// @param property A pipeline property.
    /// @param element  An element's id.
    /// @param name     A property's name.
    /// @return True if the property is that one.
    static boolean isProperty(final PipelineProperty property, final String element, final String name) {
        return Objects.equals(property.getElement(), element) && Objects.equals(property.getName(), name);
    }
}
