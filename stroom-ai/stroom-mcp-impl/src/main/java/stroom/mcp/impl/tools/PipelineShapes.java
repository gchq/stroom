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
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.MergedPipeline.ElementProperty;
import stroom.ai.impl.tools.MergedPipeline.TranslationDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.pipeline.PipelineService;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineLayer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.regex.Pattern;

/// What a pipeline does, read from its elements and properties as Stroom runs it: its stage (translation,
/// indexing, reference and so on), its element chain, what a child must supply (its XSLT, its text converter) and
/// what it already configures for its children (a decoration XSLT, an Elastic cluster).
///
/// A pipeline's stage is read by the configured stage markers ([AiConfig#getPipelineStages()]) wherever it is
/// read, not only by the template search. Seen: a translation pipeline found by the policy's markers, then classed
/// 'other' when its sample was processed, its filter made disabled for the user to enable.
///
/// A CEF pipeline writing text (to a file or a stream) has no element of its own to tell it by: it is told by its
/// XSLT ([#writesCef]), read as the user.
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
    /// The backend of a CEF pipeline that writes its lines as text (a TextWriter to a file or stream appender).
    static final String FILE = "file";
    /// How many imports deep a pipeline's XSLTs are followed for CEF lines.
    static final int MAX_IMPORT_DEPTH = 4;
    /// The most XSLTs read to tell whether a pipeline writes CEF lines.
    static final int MAX_XSLTS_READ = 25;
    // A CEF header's start, as an XSLT writing CEF lines holds it.
    private static final Pattern CEF_LITERAL = Pattern.compile("CEF:[01]\\|");

    private final Provider<PipelineService> pipelineServiceProvider;
    private final Provider<AiConfig> aiConfigProvider;
    private final Provider<DocumentAccess> documentAccessProvider;
    private final Provider<DocFinder> docFinderProvider;

    /// @param pipelineServiceProvider Reads a pipeline's layers, as the pipeline editor does.
    /// @param aiConfigProvider        Gives the stage markers.
    /// @param documentAccessProvider  Reads a pipeline's XSLTs as the user, to tell a CEF one writing text.
    /// @param docFinderProvider       Finds the XSLTs they import, by name as Stroom does.
    @Inject
    PipelineShapes(final Provider<PipelineService> pipelineServiceProvider,
                   final Provider<AiConfig> aiConfigProvider,
                   final Provider<DocumentAccess> documentAccessProvider,
                   final Provider<DocFinder> docFinderProvider) {
        this.pipelineServiceProvider = pipelineServiceProvider;
        this.aiConfigProvider = aiConfigProvider;
        this.documentAccessProvider = documentAccessProvider;
        this.docFinderProvider = docFinderProvider;
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
            LOGGER.debug(() -> LogUtil.message("Unable to load pipeline {}: {}", uuid, e.getMessage()), e);
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
            LOGGER.debug(() -> LogUtil.message("Unable to merge pipeline {}: {}", uuid, e.getMessage()), e);
            throw new PipelineUnreadableException(reason(e));
        }
    }

    /// @param uuid The pipeline's UUID.
    /// @return What it does.
    /// @throws PipelineUnreadableException if Stroom can't load it, or the user may not read it.
    Shape shape(final String uuid) {
        final MergedPipeline merged = merged(uuid);
        return shape(merged, markers(), () -> writesCef(merged, uuid));
    }

    /// Whether a pipeline's XSLTs write CEF lines: one of them (its own or inherited, or one they import or
    /// include, up to [#MAX_IMPORT_DEPTH] deep) keeps a CEF plan (as draft_cef_mapping saves it) or holds a CEF
    /// header's start (`CEF:0|` or `CEF:1|`). Each is read as the user; one that can't be found or read is skipped.
    ///
    /// @param merged The pipeline as Stroom runs it.
    /// @param uuid   Its UUID.
    /// @return True if its XSLTs write CEF lines; false if not, or if they can't be read.
    boolean writesCef(final MergedPipeline merged, final String uuid) {
        try {
            final DocumentAccess documentAccess = documentAccessProvider.get();
            final List<DocRef> xslts = merged.translationDocs(uuid).stream()
                    .map(TranslationDoc::doc)
                    .filter(doc -> XsltDoc.TYPE.equals(doc.getType()))
                    .toList();
            return writesCef(xslts, ref -> documentAccess.read(ref, XsltDoc.class),
                    href -> XsltImports.resolve(docFinderProvider.get(), documentAccess, href));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to read the XSLTs of pipeline {}: {}", uuid,
                    e.getMessage()), e);
            return false;
        }
    }

    /// Whether these XSLTs, or those they import or include, write CEF lines (see [#writesCef(MergedPipeline,
    /// String)]): followed breadth first, up to [#MAX_IMPORT_DEPTH] imports deep and [#MAX_XSLTS_READ] read.
    ///
    /// @param xslts    A pipeline's XSLTs.
    /// @param read     Reads an XSLT, or empty if it can't be.
    /// @param imported Finds the XSLT an import's href names, or empty if none can be.
    /// @return True if one of them writes CEF lines.
    static boolean writesCef(final List<DocRef> xslts,
                             final Function<DocRef, Optional<XsltDoc>> read,
                             final Function<String, Optional<XsltDoc>> imported) {
        final Set<String> seen = new HashSet<>();
        final Set<String> hrefs = new HashSet<>();
        List<XsltDoc> level = new ArrayList<>();
        for (final DocRef ref : xslts) {
            if (seen.add(ref.getUuid()) && seen.size() <= MAX_XSLTS_READ) {
                read.apply(ref).ifPresent(level::add);
            }
        }
        for (int depth = 0; depth <= MAX_IMPORT_DEPTH && !level.isEmpty(); depth++) {
            final List<XsltDoc> next = new ArrayList<>();
            for (final XsltDoc xslt : level) {
                if (writesCef(xslt)) {
                    return true;
                }
                for (final String href : SharedXslt.importsOf(NullSafe.string(xslt.getData()))) {
                    if (seen.size() >= MAX_XSLTS_READ) {
                        break;
                    }
                    if (!hrefs.add(href)) {
                        continue;
                    }
                    imported.apply(href)
                            .filter(doc -> seen.add(doc.getUuid()))
                            .ifPresent(next::add);
                }
            }
            level = next;
        }
        return false;
    }

    // Whether this XSLT keeps a CEF plan, or holds a CEF header's start.
    private static boolean writesCef(final XsltDoc xslt) {
        final boolean keptCef = KeptMapping.readMapping(xslt.getDescription())
                .filter(kept -> XsltSaves.CEF.equals(kept.kind()))
                .isPresent();
        return keptCef || CEF_LITERAL.matcher(NullSafe.string(xslt.getData())).find();
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
        return shape(merged, markers, () -> false);
    }

    /// @param merged    A pipeline as Stroom runs it.
    /// @param markers   Each stage's markers, in the order they are tested.
    /// @param writesCef Whether its XSLTs write CEF lines; asked only of a pipeline that would otherwise be 'other'.
    /// @return What it does.
    static Shape shape(final MergedPipeline merged,
                       final Map<String, AiPipelineStageConfig> markers,
                       final BooleanSupplier writesCef) {
        final Map<String, String> elements = merged.elements();
        final Map<ElementProperty, Object> properties = merged.properties();
        final Classification classification = classify(elements, properties, markers, writesCef);
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
        return classify(elements, properties, markers, () -> false);
    }

    /// @param elements   Each element's type by its id.
    /// @param properties Each property's value by its element and name.
    /// @param markers    Each stage's markers, in the order they are tested.
    /// @param writesCef  Whether its XSLTs write CEF lines ([#writesCef(MergedPipeline, String)]); asked only of a
    ///                   pipeline that would otherwise be 'other'.
    /// @return The pipeline's stage, and its index backend for an indexing one or how a forwarding one sends.
    static Classification classify(final Map<String, String> elements,
                                   final Map<ElementProperty, Object> properties,
                                   final Map<String, AiPipelineStageConfig> markers,
                                   final BooleanSupplier writesCef) {
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
        // (A text writer can't tell: Batch Search writes text from XML too. A CEF pipeline writing text is told by
        // its XSLT, below.)
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
        // Events written as CEF lines of text (to a file or a stream): its XSLT says so, read only now. Raw input is
        // a source's own CEF being parsed, not CEF output.
        final boolean rawInput = elements.values().stream().anyMatch(RAW_PARSERS::contains);
        if (!rawInput && writesCef.getAsBoolean()) {
            return new Classification("forwarding", FILE);
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
    /// @param backend lucene or elasticsearch for an indexing pipeline; kafka or file (CEF lines of text) for a
    ///                forwarding one, or null when its markers alone class it; else null.
    record Classification(String stage, String backend) {

    }

    /// What a pipeline does.
    ///
    /// @param stage          Its stage.
    /// @param backend        Its index backend, or how a forwarding one sends (kafka or file), or null.
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

        /// @return It as a result gives it, without its properties.
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
