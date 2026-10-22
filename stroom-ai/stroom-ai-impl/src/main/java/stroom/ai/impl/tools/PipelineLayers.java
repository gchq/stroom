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

package stroom.ai.impl.tools;

import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDataMerger;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineLayer;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// A pipeline with the templates it inherits from, merged as Stroom builds it: its elements and their types, the
/// XSLTs and text converters it runs and whether each is its own or its template's, and the elements it sets
/// itself, as opposed to ones inherited unchanged (errors in those are its own doing; errors in the rest come from
/// its template).
///
/// @param doc    The pipeline.
/// @param layers Its layers, the root template first and the pipeline itself last.
/// @param merger The merged pipeline.
public record PipelineLayers(PipelineDoc doc, List<PipelineLayer> layers, PipelineDataMerger merger) {

    /// The properties that hold a translation's code, and the document types they name.
    public static final Map<String, String> CODE_PROPERTIES = Map.of("xslt", XsltDoc.TYPE,
            "textConverter", TextConverterDoc.TYPE);
    /// The most templates followed up.
    static final int MAX_INHERITANCE = 20;

    private static final String XSLT_FILTER = "XSLTFilter";

    /// Reads a pipeline and the templates it inherits from, as the user.
    ///
    /// @param documentAccess The user's access to documents.
    /// @param uuid           The pipeline's UUID.
    /// @return The pipeline, merged; empty if it, or a template it inherits from, can't be read (failing closed:
    /// what it inherits can't be known), or it can't be merged.
    public static Optional<PipelineLayers> load(final DocumentAccess documentAccess, final String uuid) {
        final Optional<DocRef> pipelineRef = documentAccess.findDocRef(uuid)
                .filter(docRef -> PipelineDoc.TYPE.equals(docRef.getType()));
        if (pipelineRef.isEmpty()) {
            return Optional.empty();
        }
        final List<PipelineLayer> layers = new ArrayList<>();
        final Set<String> seen = new LinkedHashSet<>();
        PipelineDoc pipeline = null;
        DocRef current = pipelineRef.get();
        while (current != null) {
            if (!seen.add(current.getUuid()) || seen.size() > MAX_INHERITANCE) {
                return Optional.empty();
            }
            final Optional<PipelineDoc> doc = documentAccess.read(current, PipelineDoc.class);
            if (doc.isEmpty()) {
                return Optional.empty();
            }
            if (pipeline == null) {
                pipeline = doc.get();
            }
            if (doc.get().getPipelineData() != null) {
                layers.addFirst(new PipelineLayer(doc.get().asDocRef(), doc.get().getPipelineData()));
            }
            current = doc.get().getParentPipeline();
        }
        try {
            return Optional.of(new PipelineLayers(pipeline, List.copyOf(layers),
                    new PipelineDataMerger().merge(layers)));
        } catch (final RuntimeException e) {
            // E.g. a PipelineModelException: an element type Stroom no longer has.
            return Optional.empty();
        }
    }

    /// @return Each element's type, by its id, in the order the layers add them (the root template's first).
    public Map<String, String> types() {
        final Map<String, String> types = new LinkedHashMap<>();
        for (final PipelineLayer layer : layers) {
            for (final PipelineElement element : NullSafe.list(layer.getPipelineData().getAddedElements())) {
                if (merger.getElements().containsKey(element.getId())) {
                    types.putIfAbsent(element.getId(), element.getType());
                }
            }
        }
        // Any the merge adds itself (a Source element for an old pipeline).
        merger.getElements().forEach((id, element) -> types.putIfAbsent(id, element.getType()));
        return types;
    }

    /// @return The elements the pipeline itself adds or configures (sets a property or reference on), as opposed
    /// to ones inherited unchanged from its template.
    public Set<String> ownElements() {
        if (layers.isEmpty()) {
            return Set.of();
        }
        final PipelineData data = layers.getLast().getPipelineData();
        final Set<String> own = new LinkedHashSet<>();
        NullSafe.list(data.getAddedElements()).forEach(element -> own.add(element.getId()));
        NullSafe.list(data.getAddedProperties()).forEach(property -> own.add(property.getElement()));
        NullSafe.list(data.getAddedPipelineReferences()).forEach(reference -> own.add(reference.getElement()));
        return own;
    }

    /// @return The XSLTs and text converters the pipeline runs, each with `element`, `element_type`, `doc` (`type`,
    /// `uuid`, `name`), `set_by` (the pipeline whose layer sets it) and `inherited_from_template`, as the Stroom
    /// MCP server gave them.
    public List<Map<String, Object>> translationDocs() {
        final Map<String, String> types = types();
        final List<Map<String, Object>> docs = new ArrayList<>();
        // In the order of the elements, so the same pipeline is always described the same way.
        for (final String elementId : types.keySet()) {
            for (final PipelineProperty property : NullSafe.map(merger.getProperties().get(elementId)).values()) {
                final DocRef entity = NullSafe.get(property.getValue(), value -> value.getEntity());
                if (!CODE_PROPERTIES.containsKey(property.getName()) || entity == null) {
                    continue;
                }
                final DocRef source = merger.getPropertySource(property);
                final Map<String, Object> doc = new LinkedHashMap<>();
                doc.put("element", property.getElement());
                doc.put("element_type", types.get(property.getElement()));
                doc.put("doc", docRef(entity));
                doc.put("set_by", NullSafe.get(source, DocRef::getName));
                doc.put("inherited_from_template", source != null && source.getUuid() != null
                                                   && !source.getUuid().equals(doc().getUuid()));
                docs.add(doc);
            }
        }
        return docs;
    }

    /// @return The pipeline's own XSLT steps, which is where its translation happens; with no XSLT of its own, the
    /// last XSLT it inherits; with no XSLT at all (a pipeline writing its parser's records as Records), the
    /// parser, whose output is what is written.
    public List<String> defaultOutputs() {
        final Map<String, String> types = types();
        final Set<String> own = ownElements();
        final List<String> ownXslt = types.entrySet().stream()
                .filter(entry -> own.contains(entry.getKey()) && XSLT_FILTER.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        if (!ownXslt.isEmpty()) {
            return ownXslt;
        }
        final List<String> xslt = types.entrySet().stream()
                .filter(entry -> XSLT_FILTER.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        if (!xslt.isEmpty()) {
            return List.of(xslt.getLast());
        }
        return types.entrySet().stream()
                .filter(entry -> isParser(entry.getValue()))
                .map(Map.Entry::getKey)
                .limit(1)
                .toList();
    }

    /// @return The first element that parses the input (its type ends with `Parser`), or null.
    public String parser() {
        return types().entrySet().stream()
                .filter(entry -> isParser(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    /// @return The pipeline references (reference loaders) it has, as merged.
    public List<PipelineReference> references() {
        return merger.getPipelineReferences().values().stream()
                .flatMap(byName -> byName.values().stream())
                .flatMap(List::stream)
                .toList();
    }

    /// @return The merged elements.
    public Map<String, PipelineElement> elements() {
        return merger.getElements();
    }

    private static boolean isParser(final String type) {
        return type != null && type.endsWith("Parser");
    }

    /// @return A document reference as the Stroom MCP server gave one: `type`, `uuid` and `name`.
    public static Map<String, Object> docRef(final DocRef docRef) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", docRef.getType());
        map.put("uuid", docRef.getUuid());
        map.put("name", docRef.getName());
        return map;
    }
}
