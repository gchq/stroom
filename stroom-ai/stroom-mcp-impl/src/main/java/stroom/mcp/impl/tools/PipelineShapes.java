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
import stroom.ai.impl.AiPipelineStageConfig;
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.MergedPipeline.ElementProperty;
import stroom.docref.DocRef;
import stroom.pipeline.PipelineService;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineLayer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// What a pipeline does, read from its elements and properties as Stroom runs it: its stage (translation,
/// indexing, reference and so on), its element chain, what a child must supply (its XSLT, its text converter) and
/// what it already configures for its children (a decoration XSLT, an Elastic cluster).
///
/// A pipeline's stage is read by the configured stage markers ([AiConfig#getPipelineStages()]) wherever it is
/// read, not only by the template search. Seen: a translation pipeline found by the policy's markers, then classed
/// 'other' when its sample was processed, its filter made disabled for the user to enable.
class PipelineShapes {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PipelineShapes.class);

    /// The property that makes each element type do something; unset means a child must supply it.
    static final Map<String, List<String>> KEY_PROPERTIES = keyProperties();
    /// The build subfolder a pipeline of each stage belongs in.
    static final Map<String, String> STAGE_ROLES = Map.of("translation", "Events", "records", "Records",
            "indexing", "Indexing", "discovery", "Indexing", "reference", "Reference", "forwarding", "CEF");

    private static final Map<String, String> INDEXING = Map.of("IndexingFilter", "lucene",
            "ElasticIndexingFilter", "elasticsearch");
    private static final Set<String> RAW_PARSERS = Set.of("JSONParser", "DSParser", "CombinedParser",
            "XMLFragmentParser");

    private final Provider<PipelineService> pipelineServiceProvider;
    private final Provider<AiConfig> aiConfigProvider;

    /// @param pipelineServiceProvider Reads a pipeline's layers, as the pipeline editor does.
    /// @param aiConfigProvider        Gives the stage markers.
    @Inject
    PipelineShapes(final Provider<PipelineService> pipelineServiceProvider,
                   final Provider<AiConfig> aiConfigProvider) {
        this.pipelineServiceProvider = pipelineServiceProvider;
        this.aiConfigProvider = aiConfigProvider;
    }

    /// Reads a pipeline's layers as Stroom builds them, root template first: each is validated, so a pipeline
    /// Stroom can't load (e.g. a property its element type doesn't have) is refused here, with Stroom's reason.
    ///
    /// @param uuid The pipeline's UUID.
    /// @return Its layers.
    /// @throws PipelineUnreadableException if Stroom can't load it, or the user may not read it.
    List<PipelineLayer> layers(final String uuid) {
        try {
            return NullSafe.list(pipelineServiceProvider.get().fetchPipelineLayers(
                    new DocRef(PipelineDoc.TYPE, uuid)));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to load pipeline " + uuid + ": " + e.getMessage(), e);
            throw new PipelineUnreadableException(reason(e));
        }
    }

    /// @param uuid The pipeline's UUID.
    /// @return The pipeline as Stroom runs it.
    /// @throws PipelineUnreadableException if Stroom can't load it, or the user may not read it.
    MergedPipeline merged(final String uuid) {
        final List<PipelineLayer> layers = layers(uuid);
        try {
            return MergedPipeline.of(layers);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to merge pipeline " + uuid + ": " + e.getMessage(), e);
            throw new PipelineUnreadableException(reason(e));
        }
    }

    /// @param uuid The pipeline's UUID.
    /// @return What it does.
    /// @throws PipelineUnreadableException if Stroom can't load it, or the user may not read it.
    Shape shape(final String uuid) {
        return shape(merged(uuid), markers());
    }

    /// @param uuid The UUID of a pipeline, or of a template a pipeline is to be made from.
    /// @return The build subfolder the pipeline belongs in, from its stage, or null when the stage can't be read
    /// (it then goes at the build's top).
    String role(final String uuid) {
        try {
            return STAGE_ROLES.get(shape(uuid).stage());
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /// @return Each stage's markers, in the order a pipeline is tested against them.
    Map<String, AiPipelineStageConfig> markers() {
        return aiConfigProvider.get().getPipelineStages().byName();
    }

    /// @param merged  A pipeline as Stroom runs it.
    /// @param markers Each stage's markers, in the order they are tested.
    /// @return What it does.
    static Shape shape(final MergedPipeline merged, final Map<String, AiPipelineStageConfig> markers) {
        final Map<String, String> elements = merged.elements();
        final Map<ElementProperty, Object> properties = merged.properties();
        final Classification classification = classify(elements, properties, markers);
        final List<String> chain = merged.chain();
        final List<Map<String, Object>> openSlots = new ArrayList<>();
        final List<Map<String, Object>> shared = new ArrayList<>();
        for (final String element : chain) {
            final String type = elements.get(element);
            for (final String key : KEY_PROPERTIES.getOrDefault(type, List.of())) {
                Object value = properties.get(new ElementProperty(element, key));
                if ("SchemaFilter".equals(type) && isUnset(value)) {
                    // A schema filter is set by its namespace too (kafka-records:1), not only by a schema group.
                    value = unsetAsNull(properties.get(new ElementProperty(element, "namespaceURI")));
                    if (value == null) {
                        value = unsetAsNull(properties.get(new ElementProperty(element, "systemId")));
                    }
                }
                slot(element, type, key, value, openSlots, shared);
            }
        }
        final Map<String, Object> propertyValues = new LinkedHashMap<>();
        properties.forEach((key, value) -> propertyValues.put(key.element() + "." + key.name(), value));
        return new Shape(classification.stage(), classification.backend(), chain,
                chain.isEmpty()
                        ? null
                        : elements.get(chain.getFirst()),
                openSlots, shared,
                merged.references().stream().map(MergedPipeline::describe).toList(),
                propertyValues);
    }

    /// @param elements   Each element's type by its id.
    /// @param properties Each property's value by its element and name.
    /// @param markers    Each stage's markers, in the order they are tested.
    /// @return The pipeline's stage, and its index backend for an indexing one.
    static Classification classify(final Map<String, String> elements,
                                   final Map<ElementProperty, Object> properties,
                                   final Map<String, AiPipelineStageConfig> markers) {
        final List<String> indexers = elements.values().stream()
                .filter(INDEXING::containsKey)
                .toList();
        if (!indexers.isEmpty()) {
            final boolean rawInput = elements.values().stream().anyMatch(RAW_PARSERS::contains);
            return new Classification(rawInput
                    ? "discovery"
                    : "indexing", INDEXING.get(indexers.getFirst()));
        }
        if (elements.containsValue("ReferenceDataFilter")) {
            return new Classification("loader", null);
        }
        // Events sent on to Kafka (CEF for ArcSight, say).
        // (A text writer can't tell: Batch Search writes text from XML too. Text CEF pipelines are found by name.)
        if (elements.containsValue("StandardKafkaProducer")) {
            return new Classification("forwarding", "kafka");
        }
        final Set<String> groups = valuesOf(properties, "schemaGroup");
        final Set<String> types = valuesOf(properties, "streamType");
        for (final Map.Entry<String, AiPipelineStageConfig> marker : markers.entrySet()) {
            if (marker.getValue().getSchemaGroups().stream().anyMatch(groups::contains)
                || marker.getValue().getStreamTypes().stream().anyMatch(types::contains)) {
                return new Classification(marker.getKey(), null);
            }
        }
        return new Classification("other", null);
    }

    /// Records one of an element's key properties as set (shared with every child) or open (for a child to set).
    ///
    /// @param element   The element's id.
    /// @param type      The element's type.
    /// @param key       The key property.
    /// @param value     Its value, or null if unset.
    /// @param openSlots The open slots so far, added to.
    /// @param shared    The shared values so far, added to.
    static void slot(final String element,
                     final String type,
                     final String key,
                     final Object value,
                     final List<Map<String, Object>> openSlots,
                     final List<Map<String, Object>> shared) {
        if (!isUnset(value)) {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("element", element);
            entry.put("type", type);
            entry.put("property", key);
            entry.put("value", value);
            shared.add(entry);
            return;
        }
        // Stroom treats an XSLTFilter with no XSLT as a no-op: records pass through unchanged. The first
        // unset one is where the child's translation goes; later ones (e.g. an empty decoration step) are optional.
        final boolean optional = "XSLTFilter".equals(type)
                                 && openSlots.stream().anyMatch(slot -> "XSLTFilter".equals(slot.get("type")));
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("element", element);
        entry.put("type", type);
        entry.put("property", key);
        if (optional) {
            entry.put("optional", "passes records through unchanged if left unset");
        }
        openSlots.add(entry);
    }

    private static boolean isUnset(final Object value) {
        return value == null || "".equals(value);
    }

    private static Object unsetAsNull(final Object value) {
        return isUnset(value)
                ? null
                : value;
    }

    private static Set<String> valuesOf(final Map<ElementProperty, Object> properties, final String name) {
        final Set<String> values = new LinkedHashSet<>();
        properties.forEach((key, value) -> {
            if (name.equals(key.name()) && value != null) {
                values.add(String.valueOf(value));
            }
        });
        return values;
    }

    private static Map<String, List<String>> keyProperties() {
        final Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("XSLTFilter", List.of("xslt"));
        map.put("DSParser", List.of("textConverter"));
        map.put("CombinedParser", List.of("textConverter"));
        map.put("XMLFragmentParser", List.of("textConverter"));
        map.put("IndexingFilter", List.of("index"));
        map.put("ElasticIndexingFilter", List.of("cluster", "indexName"));
        map.put("SchemaFilter", List.of("schemaGroup"));
        map.put("StandardKafkaProducer", List.of("kafkaConfig"));
        return Map.copyOf(map);
    }

    /// @param e Why Stroom couldn't load a pipeline.
    /// @return Stroom's own reason, for a reader.
    static String reason(final RuntimeException e) {
        return NullSafe.nonBlankStringElse(e.getMessage(), e.getClass().getSimpleName()).strip();
    }


    // --------------------------------------------------------------------------------


    /// A pipeline's stage, and its index backend for an indexing one.
    ///
    /// @param stage   translation, indexing, discovery, reference, records, forwarding, loader or other.
    /// @param backend lucene or elasticsearch for an indexing pipeline, kafka for a forwarding one, else null.
    record Classification(String stage, String backend) {

    }

    /// What a pipeline does.
    ///
    /// @param stage          Its stage.
    /// @param backend        Its index backend, or null.
    /// @param chain          Its element ids in processing order.
    /// @param parser         The type of its first element, or null.
    /// @param childMustSupply Each key property left unset, for a child to set.
    /// @param shared         Each key property already set, for every child.
    /// @param referenceData  Its reference loaders, as `<feed> via <loader pipeline>`.
    /// @param properties     Every property's value, by `element.name`.
    record Shape(String stage,
                 String backend,
                 List<String> chain,
                 String parser,
                 List<Map<String, Object>> childMustSupply,
                 List<Map<String, Object>> shared,
                 List<String> referenceData,
                 Map<String, Object> properties) {

        /// @return It as the Stroom MCP server gave it, without its properties.
        Map<String, Object> toMap() {
            final Map<String, Object> map = new LinkedHashMap<>();
            map.put("stage", stage);
            map.put("backend", backend);
            map.put("chain", chain);
            map.put("parser", parser);
            map.put("child_must_supply", childMustSupply);
            map.put("shared", shared);
            map.put("reference_data", referenceData);
            return map;
        }
    }
}
