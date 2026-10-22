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

import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDataMerger;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineLayer;
import stroom.pipeline.shared.data.PipelineLink;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// A pipeline as Stroom runs it: its layers (root template first, the pipeline itself last) merged as Stroom merges
/// them ([PipelineDataMerger]), with each element, link, property and reference in the order the layers give
/// them. The `Source` element Stroom always adds is left out, so a chain starts with the pipeline's parser.
final class MergedPipeline {

    private static final String SOURCE = "Source";
    /// The properties that name the code a pipeline runs, and the type of document each names.
    static final Map<String, String> CODE_PROPERTIES = Map.of("xslt", "XSLT", "textConverter", "TextConverter");

    private final Map<String, String> elements;
    private final Map<String, List<String>> links;
    private final Map<ElementProperty, Object> properties;
    private final Map<ElementProperty, DocRef> propertySources;
    private final List<PipelineReference> references;

    private MergedPipeline(final Map<String, String> elements,
                           final Map<String, List<String>> links,
                           final Map<ElementProperty, Object> properties,
                           final Map<ElementProperty, DocRef> propertySources,
                           final List<PipelineReference> references) {
        this.elements = elements;
        this.links = links;
        this.properties = properties;
        this.propertySources = propertySources;
        this.references = references;
    }

    /// @param layers A pipeline's layers, root template first.
    /// @return The pipeline as Stroom runs it.
    /// @throws stroom.pipeline.shared.PipelineModelException if Stroom can't merge them.
    static MergedPipeline of(final List<PipelineLayer> layers) {
        final PipelineDataMerger merger = new PipelineDataMerger().merge(layers);
        final List<PipelineData> data = NullSafe.list(layers).stream()
                .filter(Objects::nonNull)
                .map(PipelineLayer::getPipelineData)
                .filter(Objects::nonNull)
                .toList();

        final Map<String, String> elements = new LinkedHashMap<>();
        for (final PipelineData layer : data) {
            for (final PipelineElement element : NullSafe.list(layer.getAddedElements())) {
                final PipelineElement merged = merger.getElements().get(element.getId());
                if (merged != null && merged.getType() != null && !SOURCE.equals(merged.getType())) {
                    elements.putIfAbsent(merged.getId(), merged.getType());
                }
            }
        }

        final Map<String, List<String>> links = new LinkedHashMap<>();
        for (final List<PipelineLink> from : merger.getLinks().values()) {
            for (final PipelineLink link : from) {
                if (elements.containsKey(link.getFrom()) && elements.containsKey(link.getTo())) {
                    links.computeIfAbsent(link.getFrom(), k -> new ArrayList<>()).add(link.getTo());
                }
            }
        }

        final Map<ElementProperty, Object> properties = new LinkedHashMap<>();
        final Map<ElementProperty, DocRef> sources = new LinkedHashMap<>();
        for (final PipelineData layer : data) {
            for (final PipelineProperty added : NullSafe.list(layer.getAddedProperties())) {
                final PipelineProperty merged = NullSafe.get(merger.getProperties().get(added.getElement()),
                        byName -> byName.get(added.getName()));
                if (merged != null) {
                    final ElementProperty key = new ElementProperty(merged.getElement(), merged.getName());
                    properties.put(key, value(merged.getValue()));
                    sources.put(key, merger.getPropertySource(merged));
                }
            }
        }

        final Set<PipelineReference> mergedReferences = new HashSet<>();
        merger.getPipelineReferences().values().stream()
                .flatMap(byName -> byName.values().stream())
                .flatMap(Collection::stream)
                .forEach(mergedReferences::add);
        final Set<PipelineReference> references = new LinkedHashSet<>();
        for (final PipelineData layer : data) {
            for (final PipelineReference reference : NullSafe.list(layer.getAddedPipelineReferences())) {
                if (mergedReferences.contains(reference)) {
                    references.add(reference);
                }
            }
        }
        return new MergedPipeline(elements, links, properties, sources, List.copyOf(references));
    }

    /// @return Each element's type by its id, leaving out `Source`.
    Map<String, String> elements() {
        return elements;
    }

    /// @return Each property's value by its element and name: a document as `{type, uuid, name}`, else the
    /// string, number or boolean.
    Map<ElementProperty, Object> properties() {
        return properties;
    }

    /// @return The pipeline's reference loaders: its references, with what it inherits.
    List<PipelineReference> references() {
        return references;
    }

    /// @return The element ids in processing order, following links from each element nothing feeds into.
    List<String> chain() {
        final Set<String> targets = new HashSet<>();
        links.values().forEach(targets::addAll);
        final List<String> order = new ArrayList<>();
        final List<String> stack = new ArrayList<>();
        final List<String> roots = elements.keySet().stream()
                .filter(id -> !targets.contains(id))
                .toList();
        for (int i = roots.size() - 1; i >= 0; i--) {
            stack.add(roots.get(i));
        }
        while (!stack.isEmpty()) {
            final String current = stack.removeLast();
            if (order.contains(current)) {
                continue;
            }
            order.add(current);
            final List<String> children = links.getOrDefault(current, List.of());
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.add(children.get(i));
            }
        }
        return order;
    }

    /// @param pipelineUuid The pipeline's UUID.
    /// @return The XSLTs and text converters the pipeline runs, and whether each is its own or its template's.
    List<TranslationDoc> translationDocs(final String pipelineUuid) {
        final List<TranslationDoc> docs = new ArrayList<>();
        properties.forEach((key, value) -> {
            if (CODE_PROPERTIES.containsKey(key.name()) && value instanceof final Map<?, ?> doc) {
                final DocRef source = propertySources.get(key);
                final String sourceUuid = NullSafe.get(source, DocRef::getUuid);
                docs.add(new TranslationDoc(key.element(), elements.get(key.element()),
                        new DocRef(String.valueOf(doc.get("type")), String.valueOf(doc.get("uuid")),
                                doc.get("name") == null
                                        ? null
                                        : String.valueOf(doc.get("name"))),
                        NullSafe.get(source, DocRef::getName),
                        sourceUuid != null && !sourceUuid.equals(pipelineUuid)));
            }
        });
        return docs;
    }

    /// @param reference A reference loader.
    /// @return It in words, as the Stroom MCP server gave it: `<feed> via <loader pipeline>`.
    static String describe(final PipelineReference reference) {
        return NullSafe.get(reference.getFeed(), DocRef::getName) + " via "
               + NullSafe.get(reference.getPipeline(), DocRef::getName);
    }

    /// @param value A property's value.
    /// @return It as JSON has it: a document as `{type, uuid, name}`, else the string, number or boolean.
    static Object value(final PipelinePropertyValue value) {
        if (value == null) {
            return null;
        }
        if (value.getEntity() != null) {
            return docRef(value.getEntity());
        }
        if (value.getString() != null) {
            return value.getString();
        }
        if (value.getInteger() != null) {
            return value.getInteger();
        }
        if (value.getLong() != null) {
            return value.getLong();
        }
        return value.getBoolean();
    }

    /// @param docRef A document, or null.
    /// @return It as `{type, uuid, name}`, or null.
    static Map<String, Object> docRef(final DocRef docRef) {
        if (docRef == null) {
            return null;
        }
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", docRef.getType());
        map.put("uuid", docRef.getUuid());
        map.put("name", docRef.getName());
        return map;
    }


    // --------------------------------------------------------------------------------


    /// A property of an element.
    ///
    /// @param element The element's id.
    /// @param name    The property's name.
    record ElementProperty(String element, String name) {

    }

    /// An XSLT or text converter a pipeline runs.
    ///
    /// @param element               The element that runs it.
    /// @param elementType           That element's type.
    /// @param doc                   The document.
    /// @param setBy                 The name of the pipeline (layer) that set it.
    /// @param inheritedFromTemplate True if a template the pipeline inherits from set it, not the pipeline itself.
    record TranslationDoc(String element, String elementType, DocRef doc, String setBy,
                          boolean inheritedFromTemplate) {

    }
}
