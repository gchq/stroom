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

import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.validation.XmlTrees;
import stroom.docref.DocRef;
import stroom.mcp.impl.tools.PipelineIndex.Entry;
import stroom.mcp.impl.tools.PipelineShapes.Shape;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/// Reference data an environment already loads: the maps stroom:lookup() can read, where they come from, and the
/// loader pipelines that serve them.
class ReferenceData {

    static final String REF_NS = "reference-data:2";
    static final String LOADER_TYPE = "ReferenceDataFilter";

    private final PipelineIndex pipelineIndex;
    private final PipelineShapes pipelineShapes;

    /// @param pipelineIndex  Lists the pipelines.
    /// @param pipelineShapes Reads what each does.
    @Inject
    ReferenceData(final PipelineIndex pipelineIndex, final PipelineShapes pipelineShapes) {
        this.pipelineIndex = pipelineIndex;
        this.pipelineShapes = pipelineShapes;
    }

    /// The reference maps an XSLT writes: for each `<reference>`, the map name (literal, or the expression it
    /// comes from), the key expression and the value's shape (text, or its element names).
    ///
    /// @param xslt An XSLT's text.
    /// @return Each map it writes, as `{map, key, value, ranges}`; empty if it doesn't parse.
    static List<Map<String, Object>> mapsInXslt(final String xslt) {
        final Optional<Element> root = XmlTrees.tryParse(xslt);
        if (root.isEmpty()) {
            return List.of();
        }
        final Map<String, Map<String, Object>> found = new LinkedHashMap<>();
        for (final Element reference : XmlTrees.named(root.get(), REF_NS, "reference")) {
            final Optional<Element> nameNode = XmlTrees.child(reference, REF_NS, "map");
            final Optional<Element> keyNode = XmlTrees.child(reference, REF_NS, "key");
            final Optional<Element> valueNode = XmlTrees.child(reference, REF_NS, "value");
            final String name = nameNode.map(ReferenceData::textOrSelect)
                    .orElse("?")
                    .replaceAll("^'(.*)'$", "$1");
            final Set<String> elements = new TreeSet<>();
            valueNode.ifPresent(value -> {
                for (final Element child : XmlTrees.descendantsAndSelf(value)) {
                    if (child == value) {
                        continue;
                    }
                    if (XmlTrees.is(child, XmlTrees.XSL, "element")) {
                        NullSafe.consume(XmlTrees.attribute(child, "name"), elements::add);
                    } else if (!XmlTrees.XSL.equals(child.getNamespaceURI())) {
                        elements.add(child.getLocalName());
                    }
                }
            });
            final Map<String, Object> entry = found.computeIfAbsent(name, k -> {
                final Map<String, Object> map = new LinkedHashMap<>();
                map.put("map", k);
                map.put("key", keyNode.map(ReferenceData::textOrSelect).orElse(""));
                map.put("value", "text");
                map.put("ranges", false);
                return map;
            });
            if (!elements.isEmpty()) {
                entry.put("value", List.copyOf(elements));
            }
            if (XmlTrees.child(reference, REF_NS, "range").isPresent()) {
                entry.put("ranges", true);
            }
        }
        return List.copyOf(found.values());
    }

    /// Pipelines that load reference data: a ReferenceDataFilter reading streams as they are (no XSLT left for a
    /// child to set), whatever they're called and wherever they are.
    ///
    /// @param index Every pipeline the user can view.
    /// @return Each loader as `{uuid, name}`.
    List<Map<String, Object>> loaderPipelines(final Map<String, Entry> index) {
        final List<Map<String, Object>> loaders = new ArrayList<>();
        for (final Entry pipeline : index.values()) {
            final boolean addsLoader = NullSafe.list(NullSafe.get(pipeline.own(), PipelineData::getAddedElements))
                    .stream()
                    .map(PipelineElement::getType)
                    .anyMatch(LOADER_TYPE::equals);
            if (!addsLoader) {
                continue;
            }
            final Shape shape;
            try {
                shape = pipelineShapes.shape(pipeline.uuid());
            } catch (final PipelineUnreadableException e) {
                // Stroom can't build it (seen: a StateFilter this Stroom lacks): no loader to use.
                continue;
            }
            // A loader reads Reference streams as they are: no XSLT left for a child (a parser's text converter
            // may be unset; Stroom's own loader leaves its CombinedParser's empty).
            if ("loader".equals(shape.stage())
                && shape.childMustSupply().stream().noneMatch(slot -> "xslt".equals(slot.get("property")))) {
                loaders.add(ref(pipeline.uuid(), pipeline.name()));
            }
        }
        return loaders;
    }

    /// @param index Every pipeline the user can view.
    /// @return The pipelines that add reference loaders of their own.
    static List<Entry> referencing(final Map<String, Entry> index) {
        return index.values().stream()
                .filter(pipeline -> !NullSafe.list(NullSafe.get(pipeline.own(),
                        PipelineData::getAddedPipelineReferences)).isEmpty())
                .toList();
    }

    /// @param index Every pipeline the user can view.
    /// @param uuid  A document's UUID, e.g. an XSLT's.
    /// @return The pipelines whose own properties name the document.
    static List<Entry> naming(final Map<String, Entry> index, final String uuid) {
        return index.values().stream()
                .filter(pipeline -> NullSafe.list(NullSafe.get(pipeline.own(), PipelineData::getAddedProperties))
                        .stream()
                        .map(PipelineProperty::getValue)
                        .filter(Objects::nonNull)
                        .anyMatch(value -> value.getEntity() != null
                                           && uuid.equals(value.getEntity().getUuid())))
                .toList();
    }

    /// The loader for a reference feed, when the agent names none: the one other pipelines already load that feed
    /// with, else the environment's only loader. Several, and none in use: the agent asks which.
    ///
    /// @param feed The reference feed's name.
    /// @return The loader pipeline's UUID.
    /// @throws ToolRefusal if it can't be told.
    String resolveLoader(final String feed) {
        final Map<String, Entry> index = pipelineIndex.read();
        for (final Entry pipeline : referencing(index)) {
            final List<PipelineReference> references;
            try {
                references = pipelineShapes.merged(pipeline.uuid()).references();
            } catch (final PipelineUnreadableException e) {
                // A pipeline Stroom can no longer load: not the one to learn the loader from.
                continue;
            }
            for (final PipelineReference reference : references) {
                if (Objects.equals(NullSafe.get(reference.getFeed(), DocRef::getName), feed)
                    && NullSafe.get(reference.getPipeline(), DocRef::getUuid) != null) {
                    return reference.getPipeline().getUuid();
                }
            }
        }
        final List<Map<String, Object>> loaders = loaderPipelines(index);
        if (loaders.size() == 1) {
            return String.valueOf(loaders.getFirst().get("uuid"));
        }
        throw new ToolRefusal("Which pipeline loads reference feed " + feed + "? "
                              + (loaders.isEmpty()
                ? "This Stroom has no reference-data loader (a pipeline with a ReferenceDataFilter): ask the user."
                : "This Stroom has several loaders: " + String.join(", ", loaders.stream()
                        .map(loader -> String.valueOf(loader.get("name")))
                        .toList()) + "; name one as loader_pipeline."));
    }

    /// @param uuid A document's UUID.
    /// @param name Its name.
    /// @return It as `{uuid, name}`.
    static Map<String, Object> ref(final String uuid, final String name) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("uuid", uuid);
        map.put("name", name);
        return map;
    }

    // A <map> or <key> element's content as the reader would say it: literal text, or the select expression.
    private static String textOrSelect(final Element node) {
        final String literal = XmlTrees.text(node);
        if (!literal.isEmpty()) {
            return literal;
        }
        for (final Element child : XmlTrees.descendantsAndSelf(node)) {
            if (XmlTrees.is(child, XmlTrees.XSL, "value-of") || XmlTrees.is(child, XmlTrees.XSL, "sequence")) {
                return Objects.requireNonNullElse(XmlTrees.attribute(child, "select"), "");
            }
        }
        return Objects.requireNonNullElse(node.getTextContent(), "").strip();
    }
}
