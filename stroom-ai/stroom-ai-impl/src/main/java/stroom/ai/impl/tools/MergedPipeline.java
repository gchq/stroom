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
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataBuilder;
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
import java.util.Optional;
import java.util.Set;

/// A pipeline as Stroom runs it: its layers (root template first, the pipeline itself last) merged as Stroom merges
/// them ([PipelineDataMerger]), with each element, link, property and reference in the order the layers give
/// them. The `Source` element Stroom always adds is left out, so a chain starts with the pipeline's parser.
///
/// It also says which elements the pipeline sets itself, as opposed to ones inherited unchanged from its template
/// (errors in those are its own doing; errors in the rest come from its template), and where its output is.
public final class MergedPipeline {

    private static final String SOURCE = "Source";
    private static final String XSLT_FILTER = "XSLTFilter";
    /// The most templates a pipeline is followed up through.
    static final int MAX_INHERITANCE = 20;
    /// The properties that name the code a pipeline runs, and the type of document each names.
    public static final Map<String, String> CODE_PROPERTIES = Map.of("xslt", "XSLT", "textConverter", "TextConverter");

    private final Map<String, String> elements;
    private final Map<String, List<String>> links;
    private final Map<ElementProperty, Object> properties;
    private final Map<ElementProperty, DocRef> propertySources;
    private final List<PipelineReference> references;
    private final DocRef pipeline;
    private final Set<String> ownElements;
    private final List<DocRef> templateChain;
    private final Map<String, List<Map<String, Object>>> ownRemovals;
    private final PipelineDoc doc;

    private MergedPipeline(final Map<String, String> elements,
                           final Map<String, List<String>> links,
                           final Map<ElementProperty, Object> properties,
                           final Map<ElementProperty, DocRef> propertySources,
                           final List<PipelineReference> references,
                           final DocRef pipeline,
                           final Set<String> ownElements,
                           final List<DocRef> templateChain,
                           final Map<String, List<Map<String, Object>>> ownRemovals,
                           final PipelineDoc doc) {
        this.elements = elements;
        this.links = links;
        this.properties = properties;
        this.propertySources = propertySources;
        this.references = references;
        this.pipeline = pipeline;
        this.ownElements = ownElements;
        this.templateChain = templateChain;
        this.ownRemovals = ownRemovals;
        this.doc = doc;
    }

    /// Reads a pipeline and the templates it inherits from, as the user, and merges them.
    ///
    /// @param documentAccess The user's access to documents.
    /// @param uuid           The pipeline's UUID.
    /// @return The pipeline, merged; empty if it, or a template it inherits from, can't be read (failing closed:
    /// what it inherits can't be known), or it can't be merged.
    public static Optional<MergedPipeline> load(final DocumentAccess documentAccess, final String uuid) {
        final Optional<DocRef> pipelineRef = documentAccess.findDocRef(uuid)
                .filter(docRef -> PipelineDoc.TYPE.equals(docRef.getType()));
        if (pipelineRef.isEmpty()) {
            return Optional.empty();
        }
        final List<PipelineLayer> layers = new ArrayList<>();
        final Set<String> seen = new LinkedHashSet<>();
        PipelineDoc pipelineDoc = null;
        DocRef current = pipelineRef.get();
        while (current != null) {
            if (!seen.add(current.getUuid()) || seen.size() > MAX_INHERITANCE) {
                return Optional.empty();
            }
            final Optional<PipelineDoc> doc = documentAccess.read(current, PipelineDoc.class);
            if (doc.isEmpty()) {
                return Optional.empty();
            }
            if (pipelineDoc == null) {
                pipelineDoc = doc.get();
            }
            layers.addFirst(new PipelineLayer(doc.get().asDocRef(), Objects.requireNonNullElseGet(
                    doc.get().getPipelineData(), () -> new PipelineDataBuilder().build())));
            current = doc.get().getParentPipeline();
        }
        try {
            final MergedPipeline merged = of(layers);
            return Optional.of(new MergedPipeline(merged.elements, merged.links, merged.properties,
                    merged.propertySources, merged.references, merged.pipeline, merged.ownElements,
                    merged.templateChain, merged.ownRemovals, pipelineDoc));
        } catch (final RuntimeException e) {
            // E.g. a PipelineModelException: an element type Stroom no longer has.
            return Optional.empty();
        }
    }

    /// @param layers A pipeline's layers, root template first.
    /// @return The pipeline as Stroom runs it.
    /// @throws stroom.pipeline.shared.PipelineModelException if Stroom can't merge them.
    public static MergedPipeline of(final List<PipelineLayer> layers) {
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
        final PipelineLayer last = NullSafe.isEmptyCollection(layers)
                ? null
                : layers.getLast();
        final Set<String> own = new LinkedHashSet<>();
        final PipelineData lastData = NullSafe.get(last, PipelineLayer::getPipelineData);
        if (lastData != null) {
            NullSafe.list(lastData.getAddedElements()).forEach(element -> own.add(element.getId()));
            NullSafe.list(lastData.getAddedProperties()).forEach(property -> own.add(property.getElement()));
            NullSafe.list(lastData.getAddedPipelineReferences()).forEach(reference ->
                    own.add(reference.getElement()));
        }
        final List<DocRef> chain = NullSafe.list(layers).stream()
                .filter(Objects::nonNull)
                .map(PipelineLayer::getSourcePipeline)
                .toList();
        return new MergedPipeline(elements, links, properties, sources, List.copyOf(references),
                NullSafe.get(last, PipelineLayer::getSourcePipeline), Set.copyOf(own), chain,
                removalsOf(lastData), null);
    }

    // What the pipeline's own layer removes from its template, by section, as the Stroom MCP server gave it: only
    // the sections it removes anything from.
    private static Map<String, List<Map<String, Object>>> removalsOf(final PipelineData data) {
        final Map<String, List<Map<String, Object>>> removals = new LinkedHashMap<>();
        if (data == null) {
            return removals;
        }
        final List<Map<String, Object>> elements = NullSafe.list(data.getRemovedElements()).stream()
                .map(element -> entry("id", element.getId(), "type", element.getType()))
                .toList();
        final List<Map<String, Object>> links = NullSafe.list(data.getRemovedLinks()).stream()
                .map(link -> entry("from", link.getFrom(), "to", link.getTo()))
                .toList();
        final List<Map<String, Object>> properties = NullSafe.list(data.getRemovedProperties()).stream()
                .map(property -> entry("element", property.getElement(), "name", property.getName(),
                        "value", value(property.getValue())))
                .toList();
        final List<Map<String, Object>> references = NullSafe.list(data.getRemovedPipelineReferences()).stream()
                .map(MergedPipeline::reference)
                .toList();
        putIfAny(removals, "elements", elements);
        putIfAny(removals, "links", links);
        putIfAny(removals, "properties", properties);
        putIfAny(removals, "pipelineReferences", references);
        return removals;
    }

    private static void putIfAny(final Map<String, List<Map<String, Object>>> removals,
                                 final String section,
                                 final List<Map<String, Object>> removed) {
        if (!removed.isEmpty()) {
            removals.put(section, removed);
        }
    }

    // A map of the names and values given, in order: name, value, name, value, ...
    private static Map<String, Object> entry(final Object... namesAndValues) {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
            map.put(String.valueOf(namesAndValues[i]), namesAndValues[i + 1]);
        }
        return map;
    }

    // A reference loader as the Stroom MCP server gave it: element, name, pipeline, feed and stream_type.
    private static Map<String, Object> reference(final PipelineReference reference) {
        return entry("element", reference.getElement(), "name", reference.getName(),
                "pipeline", docRef(reference.getPipeline()), "feed", docRef(reference.getFeed()),
                "stream_type", reference.getStreamType());
    }

    /// @return Each element's type by its id, leaving out `Source`.
    public Map<String, String> elements() {
        return elements;
    }

    /// @return Each property's value by its element and name: a document as `{type, uuid, name}`, else the
    /// string, number or boolean.
    public Map<ElementProperty, Object> properties() {
        return properties;
    }

    /// @return The pipeline's reference loaders: its references, with what it inherits.
    public List<PipelineReference> references() {
        return references;
    }

    /// @return The element ids in processing order, following links from each element nothing feeds into.
    public List<String> chain() {
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
    public List<TranslationDoc> translationDocs(final String pipelineUuid) {
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

    /// @return The XSLTs and text converters the pipeline runs, as the Stroom MCP server gave them: each with
    /// `element`, `element_type`, `doc` (`type`, `uuid`, `name`), `set_by` (the pipeline whose layer sets it) and
    /// `inherited_from_template`.
    public List<Map<String, Object>> translationDocMaps() {
        return translationDocs(NullSafe.get(pipeline, DocRef::getUuid)).stream()
                .map(TranslationDoc::toMap)
                .toList();
    }

    /// @return The pipeline's document, if it was read with [#load(DocumentAccess, String)]; null otherwise.
    public PipelineDoc doc() {
        return doc;
    }

    /// @return The pipelines whose layers make it up, root template first, the pipeline itself last.
    public List<DocRef> templateChain() {
        return templateChain;
    }

    /// @return What the pipeline's own layer removes from what it inherits, by section (`elements`, `links`,
    /// `properties`, `pipelineReferences`), only the sections it removes anything from: where it departs from its
    /// template, e.g. a decoration step it bypasses.
    public Map<String, List<Map<String, Object>>> ownRemovals() {
        return ownRemovals;
    }

    /// The pipeline as Stroom runs it, as the Stroom MCP server's `describe_pipeline` gave it: its template chain
    /// (root first), the element chain in processing order, its elements and links, every property value and the
    /// layer that set it, its reference loaders, and what it removes from its template; with the elements it sets
    /// itself and the code it runs ([#translationDocMaps()]).
    ///
    /// @return `{pipeline, template_chain, chain, elements, links, properties, references,
    /// removed_by_this_pipeline, own_elements, translation_docs}`.
    public Map<String, Object> description() {
        final Map<String, Object> described = new LinkedHashMap<>();
        final Map<String, Object> self = new LinkedHashMap<>();
        self.put("uuid", NullSafe.get(pipeline, DocRef::getUuid));
        self.put("name", doc != null
                ? doc.getName()
                : NullSafe.get(pipeline, DocRef::getName));
        self.put("description", NullSafe.isBlankString(NullSafe.get(doc, PipelineDoc::getDescription))
                ? null
                : doc.getDescription());
        described.put("pipeline", self);
        described.put("template_chain", templateChain.stream().map(MergedPipeline::docRef).toList());
        described.put("chain", chain());
        described.put("elements", elements.entrySet().stream()
                .map(element -> entry("id", element.getKey(), "type", element.getValue()))
                .toList());
        final List<Map<String, Object>> linkList = new ArrayList<>();
        links.forEach((from, tos) -> tos.forEach(to -> linkList.add(entry("from", from, "to", to))));
        described.put("links", linkList);
        described.put("properties", properties.entrySet().stream()
                .map(property -> entry("element", property.getKey().element(), "name", property.getKey().name(),
                        "value", property.getValue(), "from", docRef(propertySources.get(property.getKey()))))
                .toList());
        described.put("references", references.stream().map(MergedPipeline::reference).toList());
        described.put("removed_by_this_pipeline", ownRemovals);
        described.put("own_elements", ownElements.stream().sorted().toList());
        described.put("translation_docs", translationDocMaps());
        return described;
    }

    /// @return The pipeline itself (its own layer), or null if it has no layers.
    public DocRef pipeline() {
        return pipeline;
    }

    /// @return The elements the pipeline itself adds or configures (sets a property or reference on), as opposed
    /// to ones inherited unchanged from its template.
    public Set<String> ownElements() {
        return ownElements;
    }

    /// @return The pipeline's own XSLT steps, which is where its translation happens; with no XSLT of its own, the
    /// last XSLT it inherits; with no XSLT at all (a pipeline writing its parser's records as Records), the
    /// parser, whose output is what is written.
    public List<String> defaultOutputs() {
        final List<String> ownXslt = elements.entrySet().stream()
                .filter(entry -> ownElements.contains(entry.getKey()) && XSLT_FILTER.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        if (!ownXslt.isEmpty()) {
            return ownXslt;
        }
        final List<String> xslt = elements.entrySet().stream()
                .filter(entry -> XSLT_FILTER.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        if (!xslt.isEmpty()) {
            return List.of(xslt.getLast());
        }
        return NullSafe.isNonBlankString(parser())
                ? List.of(parser())
                : List.of();
    }

    /// @return The first element that parses the input (its type ends with `Parser`), or null.
    public String parser() {
        return elements.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().endsWith("Parser"))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    /// @param reference A reference loader.
    /// @return It in words, as the Stroom MCP server gave it: `<feed> via <loader pipeline>`.
    public static String describe(final PipelineReference reference) {
        return NullSafe.get(reference.getFeed(), DocRef::getName) + " via "
               + NullSafe.get(reference.getPipeline(), DocRef::getName);
    }

    /// @param value A property's value.
    /// @return It as JSON has it: a document as `{type, uuid, name}`, else the string, number or boolean.
    public static Object value(final PipelinePropertyValue value) {
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
    public static Map<String, Object> docRef(final DocRef docRef) {
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
    public record ElementProperty(String element, String name) {

    }

    /// An XSLT or text converter a pipeline runs.
    ///
    /// @param element               The element that runs it.
    /// @param elementType           That element's type.
    /// @param doc                   The document.
    /// @param setBy                 The name of the pipeline (layer) that set it.
    /// @param inheritedFromTemplate True if a template the pipeline inherits from set it, not the pipeline itself.
    public record TranslationDoc(String element, String elementType, DocRef doc, String setBy,
                                 boolean inheritedFromTemplate) {

        /// @return It as the Stroom MCP server gave it: `element`, `element_type`, `doc`, `set_by` and
        /// `inherited_from_template`.
        public Map<String, Object> toMap() {
            final Map<String, Object> map = new LinkedHashMap<>();
            map.put("element", element);
            map.put("element_type", elementType);
            map.put("doc", docRef(doc));
            map.put("set_by", setBy);
            map.put("inherited_from_template", inheritedFromTemplate);
            return map;
        }
    }
}
