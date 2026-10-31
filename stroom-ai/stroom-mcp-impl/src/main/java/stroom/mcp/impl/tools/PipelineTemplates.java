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

import stroom.ai.impl.AiPipelineStageConfig;
import stroom.ai.impl.AiPipelineStagesConfig;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.Globs;
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.MergedPipeline.TranslationDoc;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.validation.XmlTrees;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.mcp.impl.tools.PipelineIndex.Entry;
import stroom.mcp.impl.tools.PipelineShapes.Shape;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineLayer;
import stroom.pipeline.shared.data.PipelineLink;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Template pipelines (pipelines a new pipeline inherits from), how their children specialise them, and what they
/// expect of a child: the work of find_pipeline_templates and describe_template, and of the tools that make
/// pipelines from templates.
class PipelineTemplates {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PipelineTemplates.class);

    static final String CONFIGURED = "configured";
    static final String INHERITED_BY_OTHERS = "inherited_by_others";
    static final String STANDARD = "standard";
    static final String TEMPLATE_LIKE = "template_like";
    /// The most candidates a search gives.
    static final int MAX_CANDIDATES = 10;
    /// The most unreadable pipelines a search names.
    static final int MAX_UNREADABLE = 5;
    /// The most children whose shared XSLTs are read.
    static final int MAX_SHARED_PIPELINES = 10;

    private static final Map<String, Integer> ORDER = Map.of(CONFIGURED, 0, INHERITED_BY_OTHERS, 1, STANDARD, 2,
            TEMPLATE_LIKE, 3);
    private static final List<String> XPATH_ATTRIBUTES = List.of("select", "match", "test", "group-by");
    private static final Pattern NAME = Pattern.compile("[A-Z][A-Za-z]+");
    private static final String NOT_READABLE = "listed or referenced, but its document cannot be read (deleted, or "
                                               + "no permission)";

    private final PipelineIndex pipelineIndex;
    private final PipelineShapes pipelineShapes;
    private final ProcessorFilters processorFilters;
    private final DocumentAccess documentAccess;
    private final Provider<DocFinder> docFinderProvider;

    /// @param pipelineIndex     Lists the pipelines.
    /// @param pipelineShapes    Reads what each does.
    /// @param processorFilters  Gives the feeds each child's filters cover.
    /// @param documentAccess    Reads XSLTs, checking View.
    /// @param docFinderProvider Finds a shared XSLT by the name an import gives.
    @Inject
    PipelineTemplates(final PipelineIndex pipelineIndex,
                      final PipelineShapes pipelineShapes,
                      final ProcessorFilters processorFilters,
                      final DocumentAccess documentAccess,
                      final Provider<DocFinder> docFinderProvider) {
        this.pipelineIndex = pipelineIndex;
        this.pipelineShapes = pipelineShapes;
        this.processorFilters = processorFilters;
        this.documentAccess = documentAccess;
        this.docFinderProvider = docFinderProvider;
    }

    /// Finds the template pipelines for a stage, best first: configured template folders, then pipelines that
    /// working pipelines already inherit from, then Stroom's standard templates, then pipelines of their own that
    /// leave what a child supplies unset.
    ///
    /// @param stage The stage, e.g. `translation`.
    /// @return `{stage, candidates, unreadable?, hint? | xml_fragments?}`, as find_pipeline_templates gives it.
    Map<String, Object> find(final String stage) {
        final Map<String, AiPipelineStageConfig> stages = pipelineShapes.markers();
        final AiPipelineStageConfig source = stages.get(stage);
        final Map<String, Entry> index = pipelineIndex.read();
        final Map<String, Integer> children = childCounts(index);

        final List<Ranked> ranked = new ArrayList<>();
        for (final Entry pipeline : index.values()) {
            final int childCount = children.getOrDefault(pipeline.uuid(), 0);
            final String why;
            if (configured(pipeline, source)) {
                why = CONFIGURED;
            } else if (childCount >= 2) {
                why = INHERITED_BY_OTHERS;
            } else if (pipeline.path().startsWith(AiPipelineStagesConfig.TEMPLATE_PIPELINES)) {
                why = STANDARD;
            } else if (pipeline.parentUuid() == null) {
                // Neither named nor placed as one: a pipeline of its own that leaves what a child supplies (its
                // XSLT, its text converter) unset is a template by what it is, wherever it is and whatever it's
                // called.
                why = TEMPLATE_LIKE;
            } else {
                why = null;
            }
            if (why != null) {
                ranked.add(new Ranked(why, pipeline, childCount));
            }
        }
        ranked.sort(Comparator.comparing((Ranked r) -> ORDER.get(r.why()))
                .thenComparing(Ranked::children, Comparator.reverseOrder()));

        final List<Map<String, Object>> candidates = new ArrayList<>();
        final List<String> unreadable = new ArrayList<>();
        for (final Ranked candidate : ranked) {
            if (candidates.size() >= MAX_CANDIDATES) {
                break;
            }
            final Entry pipeline = candidate.pipeline();
            final Shape shape;
            try {
                shape = PipelineShapes.shape(pipelineShapes.merged(pipeline.uuid()), stages);
            } catch (final PipelineUnreadableException e) {
                // One pipeline Stroom can't build (seen: an element type this Stroom doesn't have, StateFilter)
                // must not hide the others: the whole search failed, and the agent fell back to a template by its
                // name.
                unreadable.add(pipeline.path() + "/" + pipeline.name() + ": " + shorten(e.getMessage(), 160));
                continue;
            }
            if (!stage.equals(shape.stage())
                || (TEMPLATE_LIKE.equals(candidate.why()) && shape.childMustSupply().isEmpty())) {
                continue;
            }
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("uuid", pipeline.uuid());
            entry.put("name", pipeline.name());
            entry.put("path", pipeline.path());
            entry.put("source", candidate.why());
            entry.put("children", candidate.children());
            entry.putAll(shape.toMap());
            candidates.add(entry);
        }

        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("stage", stage);
        result.put("candidates", candidates);
        if (!unreadable.isEmpty()) {
            // Left out, said: Stroom can't build them.
            result.put("unreadable", unreadable.subList(0, Math.min(MAX_UNREADABLE, unreadable.size())));
        }
        if (candidates.isEmpty()) {
            result.put("hint", "No template found for this stage; ask the user which pipeline to base it on."
                               + ("translation".equals(stage)
                    ? " Only if they have none, create_pipeline standalone='events' builds the events pipeline "
                      + "on its own, as Stroom's standard Event Data templates are made (a last resort)."
                    : "discovery".equals(stage)
                            ? " Only if they have none, create_indexing_pipeline standalone='discovery' builds "
                              + "the discovery pipeline on its own, into Elasticsearch (a last resort)."
                            : ""));
        } else if ("translation".equals(stage)
                   && candidates.stream().noneMatch(c -> "XMLFragmentParser".equals(c.get("parser"))
                                                         || "CombinedParser".equals(c.get("parser")))) {
            // The XMLParser's templates first; with none, any whose parser can be swapped (seen: a Stroom with
            // only JSON and text templates was told only to ask the user).
            List<String> xml = candidates.stream()
                    .filter(c -> "XMLParser".equals(c.get("parser")))
                    .map(c -> String.valueOf(c.get("name")))
                    .toList();
            final String whose;
            if (xml.isEmpty()) {
                xml = candidates.stream()
                        .filter(c -> PipelineWrites.PARSERS.contains(String.valueOf(c.get("parser"))))
                        .map(c -> c.get("name") + " (" + c.get("parser") + ")")
                        .toList();
                whose = "any translation template (its parser is replaced)";
            } else {
                whose = "a translation template whose parser is the XMLParser";
            }
            result.put("xml_fragments", "No template parses XML fragments (several root elements, e.g. one <Event> "
                                        + "per line). For such data, create_pipeline from " + whose
                                        + (xml.isEmpty()
                    ? ""
                    : " (" + String.join(", ", xml) + ")")
                                        + " with replace_parser='XMLFragmentParser' and an XML_FRAGMENT text "
                                        + "converter on xmlFragmentParser.textConverter (profile_sample gives the "
                                        + "wrapper).");
        }
        return result;
    }

    /// Why a pipeline counts as a template (to inherit from, not to copy): it sits in a configured template folder
    /// or Stroom's standard templates, or other pipelines inherit from it.
    ///
    /// @param uuid A pipeline's UUID.
    /// @return Why, or null for an ordinary pipeline.
    String templateReason(final String uuid) {
        final Map<String, Entry> index = pipelineIndex.read();
        final Entry entry = index.get(uuid);
        if (entry == null) {
            return null;
        }
        if (entry.path().startsWith(AiPipelineStagesConfig.TEMPLATE_PIPELINES)) {
            return "it is one of the template pipelines";
        }
        for (final Map.Entry<String, AiPipelineStageConfig> stage : pipelineShapes.markers().entrySet()) {
            if (configured(entry, stage.getValue())) {
                return "it is a configured " + stage.getKey() + " template";
            }
        }
        final List<String> children = index.values().stream()
                .filter(pipeline -> uuid.equals(pipeline.parentUuid()))
                .map(Entry::name)
                .toList();
        if (!children.isEmpty()) {
            return children.size() + " pipeline(s) inherit from it ("
                   + String.join(", ", children.subList(0, Math.min(3, children.size())))
                   + (children.size() > 3
                    ? "..."
                    : "") + ")";
        }
        if (entry.parentUuid() == null) {
            final List<Map<String, Object>> openSlots;
            try {
                openSlots = pipelineShapes.shape(uuid).childMustSupply();
            } catch (final PipelineUnreadableException e) {
                // Stroom can't build it: no template anyone uses.
                return null;
            }
            if (!openSlots.isEmpty()) {
                return "it leaves " + String.join(", ", openSlots.subList(0, Math.min(3, openSlots.size())).stream()
                        .map(String::valueOf)
                        .toList()) + " for a child to set";
            }
        }
        return null;
    }

    /// Pipelines that inherit directly from a template, each with what it overrides (properties set, elements
    /// removed or re-linked, reference loaders added) and the feeds its processor filters cover.
    ///
    /// @param templateUuid A template pipeline's UUID.
    /// @return The children, each as describe_template gives it, by name.
    List<Map<String, Object>> children(final String templateUuid) {
        final List<Entry> kids = pipelineIndex.read().values().stream()
                .filter(pipeline -> templateUuid.equals(pipeline.parentUuid()))
                .sorted(Comparator.comparing(pipeline -> NullSafe.string(pipeline.name())))
                .toList();
        final Map<String, Set<String>> feedsByPipeline = processorFilters.feedsByPipeline(processorFilters.all());
        final List<Map<String, Object>> result = new ArrayList<>();
        for (final Entry kid : kids) {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("uuid", kid.uuid());
            entry.put("name", kid.name());
            entry.put("path", kid.path());
            final List<PipelineLayer> layers;
            try {
                layers = pipelineShapes.layers(kid.uuid());
            } catch (final PipelineUnreadableException e) {
                // One pipeline Stroom cannot load (a property its element lacks, say) must not hide the others.
                entry.put("unreadable", shorten(e.getMessage(), 300));
                result.add(entry);
                continue;
            }
            final PipelineData own = layers.isEmpty()
                    ? null
                    : layers.getLast().getPipelineData();
            entry.put("sets", NullSafe.list(NullSafe.get(own, PipelineData::getAddedProperties)).stream()
                    .map((PipelineProperty property) -> property.getElement() + "." + property.getName())
                    .toList());
            entry.put("adds_elements", NullSafe.list(NullSafe.get(own, PipelineData::getAddedElements)).stream()
                    .map((PipelineElement element) -> element.getId() + " (" + element.getType() + ")")
                    .toList());
            entry.put("removes", NullSafe.list(NullSafe.get(own, PipelineData::getRemovedElements)).stream()
                    .map(PipelineElement::getId)
                    .toList());
            entry.put("adds_links", NullSafe.list(NullSafe.get(own, PipelineData::getAddedLinks)).stream()
                    .map((PipelineLink link) -> link.getFrom() + " -> " + link.getTo())
                    .toList());
            entry.put("reference_data", NullSafe.list(NullSafe.get(own, PipelineData::getAddedPipelineReferences))
                    .stream()
                    .map(MergedPipeline::describe)
                    .toList());
            entry.put("feeds", new TreeSet<>(feedsByPipeline.getOrDefault(kid.uuid(), Set.of())).stream().toList());
            result.add(entry);
        }
        return result;
    }

    /// What a child's output must contain for the template's shared elements to work: the XPath expressions and
    /// element names that the template's own XSLTs (e.g. a decoration step) read, plus its schema group and output
    /// stream type.
    ///
    /// @param templateUuid A template pipeline's UUID.
    /// @return The contract, as describe_template gives it.
    /// @throws ToolRefusal if Stroom can't load the template.
    Map<String, Object> contract(final String templateUuid) {
        final Shape shape;
        try {
            shape = pipelineShapes.shape(templateUuid);
        } catch (final PipelineUnreadableException e) {
            throw new ToolRefusal("Stroom cannot load this pipeline template: " + e.getMessage() + ". Its stored "
                                  + "settings are invalid in Stroom (e.g. a property its element type doesn't "
                                  + "have); an administrator fixes the template in the Stroom UI. Nothing to set "
                                  + "or change here: choose another pipeline template, or ask the user.");
        }
        final List<Map<String, Object>> readers = new ArrayList<>();
        for (final Map<String, Object> slot : shape.shared()) {
            if (!"XSLTFilter".equals(slot.get("type")) || !(slot.get("value") instanceof final Map<?, ?> value)) {
                continue;
            }
            readers.add(reader(String.valueOf(slot.get("element")), value));
        }
        final Map<String, Object> props = shape.properties();
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("template_uuid", templateUuid);
        result.put("stage", shape.stage());
        result.put("backend", shape.backend());
        result.put("child_must_supply", shape.childMustSupply());
        result.put("shared_readers", readers);
        result.put("schema_group", props.get("schemaFilter.schemaGroup"));
        result.put("output_stream_type", props.get("streamAppender.streamType"));
        result.put("reference_data", shape.referenceData());
        result.put("hint", readers.isEmpty()
                ? "No shared XSLT reads the child's output; only the schema applies."
                : null);
        return result;
    }

    /// The shared XSLTs (xsl:import / xsl:include) the pipelines' own XSLTs use: for each named template called,
    /// where (the element it writes, below Event or the document map) and by which pipelines.
    ///
    /// @param pipelines The pipelines, each with its `uuid` and `name`.
    /// @return The calls, most used first; then what each shared document offers; then the XSLTs that couldn't be
    /// read.
    List<Map<String, Object>> sharedXsltUsage(final List<Map<String, Object>> pipelines) {
        final SharedReader shared = new SharedReader();
        final Map<List<Object>, Map<String, Object>> seen = new LinkedHashMap<>();
        for (final Map<String, Object> pipeline : pipelines.subList(0, Math.min(MAX_SHARED_PIPELINES,
                pipelines.size()))) {
            final String uuid = String.valueOf(pipeline.get("uuid"));
            final MergedPipeline merged;
            try {
                merged = pipelineShapes.merged(uuid);
            } catch (final PipelineUnreadableException e) {
                continue;
            }
            for (final TranslationDoc entry : merged.translationDocs(uuid)) {
                if (entry.inheritedFromTemplate() || !XsltDoc.TYPE.equals(entry.doc().getType())) {
                    continue;
                }
                final String text = shared.readable(entry.doc().getUuid());
                final List<String> hrefs = text == null
                        ? List.of()
                        : SharedXslt.importsOf(text);
                if (hrefs.isEmpty()) {
                    continue;
                }
                final Map<String, String> texts = new LinkedHashMap<>();
                hrefs.forEach(href -> texts.put(href, shared.shared(href)));
                for (final Map<String, Object> use : SharedXslt.usage(text, texts)) {
                    final List<Object> key = new ArrayList<>();
                    key.add(use.get("href"));
                    key.add(use.get("template"));
                    key.add(use.get("function"));
                    key.add(use.get("at"));
                    @SuppressWarnings("unchecked") final List<Object> usedBy = (List<Object>) seen
                            .computeIfAbsent(key, k -> {
                                final Map<String, Object> row = new LinkedHashMap<>(use);
                                row.remove("within");
                                row.put("used_by", new ArrayList<>());
                                return row;
                            }).get("used_by");
                    usedBy.add(pipeline.get("name"));
                }
            }
        }
        for (final Map<String, Object> row : seen.values()) {
            final Map<String, Object> doc = shared.docs.get(String.valueOf(row.get("href")));
            if (doc != null) {
                final Map<String, Object> ref = new LinkedHashMap<>();
                ref.put("uuid", doc.get("uuid"));
                ref.put("name", row.get("href"));
                row.put("document", ref);
            }
        }
        final List<Map<String, Object>> usages = new ArrayList<>(seen.values());
        usages.sort(Comparator.comparing(row -> -((List<?>) row.get("used_by")).size()));
        // Everything each shared document offers, called or not (read_document reads its full text).
        shared.docs.forEach((href, doc) -> {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("href", href);
            entry.put("document_contents", doc);
            usages.add(entry);
        });
        shared.missing.forEach((uuid, note) -> {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("unreadable_xslt", uuid);
            entry.put("note", note);
            usages.add(entry);
        });
        return usages;
    }

    /// How this environment uses a Stroom pipeline template, before making a child of it: its children, its
    /// contract, and the shared XSLTs its children's XSLTs use.
    ///
    /// @param templateUuid A template pipeline's UUID.
    /// @return The description, as describe_template gives it.
    /// @throws ToolRefusal if Stroom can't load the template.
    Map<String, Object> describe(final String templateUuid) {
        final List<Map<String, Object>> children = children(templateUuid);
        final Map<String, Object> contract = contract(templateUuid);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("template_uuid", templateUuid);
        result.put("children", children);
        contract.forEach((key, value) -> {
            if (!"template_uuid".equals(key)) {
                result.put(key, value);
            }
        });
        List<Map<String, Object>> shared;
        try {
            shared = sharedXsltUsage(children.stream()
                    .filter(child -> !child.containsKey("unreadable"))
                    .toList());
        } catch (final RuntimeException e) {
            // The shared XSLTs are an extra: the template's children and contract still stand.
            LOGGER.debug("Unable to read the shared XSLTs of {}", templateUuid, e);
            shared = List.of();
            result.put("shared_xslt_error", shorten(PipelineShapes.reason(e), 300));
        }
        if (!shared.isEmpty()) {
            result.put("shared_xslt", shared);
            result.put("shared_xslt_hint", "These pipelines' XSLTs call named templates and functions from shared "
                                           + "XSLTs. Use the same ones in the new XSLT where they are called (the "
                                           + "mapping's or field plan's shared entries: href, template, at), and "
                                           + "do not map the elements they write: an element written twice fails "
                                           + "schema validation. Functions are called in the mapping's xpaths, "
                                           + "with a functions entry (href, prefix, namespace) for each shared "
                                           + "XSLT.");
        }
        return result;
    }

    // What one of the template's shared XSLTs reads of the child's output.
    private Map<String, Object> reader(final String element, final Map<?, ?> value) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("element", element);
        entry.put("xslt", value.get("name"));
        // A shared XSLT that is gone, empty or doesn't parse (an empty decoration XSLT is common): said for that
        // element, the rest of the contract still given.
        final Optional<XsltDoc> doc = documentAccess.read(new DocRef(XsltDoc.TYPE, String.valueOf(value.get("uuid"))),
                XsltDoc.class);
        if (doc.isEmpty()) {
            entry.put("unreadable", "the document cannot be read (deleted, or no permission)");
            return entry;
        }
        final Optional<Element> root = XmlTrees.tryParse(doc.get().getData());
        if (root.isEmpty()) {
            entry.put("unreadable", "empty or not well-formed XML");
            return entry;
        }
        final Set<String> expressions = new TreeSet<>();
        for (final Element node : XmlTrees.descendantsAndSelf(root.get())) {
            for (final String attribute : XPATH_ATTRIBUTES) {
                final String expression = XmlTrees.attribute(node, attribute);
                if (expression != null && !expression.isEmpty()) {
                    expressions.add(expression);
                }
            }
        }
        final Set<String> names = new TreeSet<>();
        for (final String expression : expressions) {
            final Matcher matcher = NAME.matcher(expression);
            while (matcher.find()) {
                names.add(matcher.group());
            }
        }
        final List<String> sorted = List.copyOf(expressions);
        entry.put("xslt", doc.get().getName());
        entry.put("reads_elements", List.copyOf(names));
        entry.put("expressions", sorted.subList(0, Math.min(60, sorted.size())));
        return entry;
    }

    private static Map<String, Integer> childCounts(final Map<String, Entry> index) {
        final Map<String, Integer> children = new LinkedHashMap<>();
        for (final Entry pipeline : index.values()) {
            if (pipeline.parentUuid() != null) {
                children.merge(pipeline.parentUuid(), 1, Integer::sum);
            }
        }
        return children;
    }

    // Whether a pipeline is in one of the stage's template folders, with a name its template names allow.
    private static boolean configured(final Entry pipeline, final AiPipelineStageConfig source) {
        if (source == null) {
            return false;
        }
        final boolean inFolder = source.getTemplateFolders().stream()
                .anyMatch(folder -> pipeline.path().equals(stripTrailingSlash(folder)));
        return inFolder && (source.getTemplateNames().isEmpty()
                            || source.getTemplateNames().stream()
                                    .anyMatch(pattern -> Globs.matches(pipeline.name(), pattern)));
    }

    private static String stripTrailingSlash(final String folder) {
        String value = folder;
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /// @param text Some text, or null.
    /// @param max  The most characters to keep.
    /// @return Its first `max` characters (empty for null).
    static String shorten(final String text, final int max) {
        final String value = Objects.requireNonNullElse(text, "");
        return value.length() > max
                ? value.substring(0, max)
                : value;
    }


    // --------------------------------------------------------------------------------


    /// A candidate template, and why it is one.
    private record Ranked(String why, Entry pipeline, int children) {

    }

    /// Reads the XSLTs one shared XSLT search needs, each once.
    private final class SharedReader {

        private final Map<String, String> texts = new LinkedHashMap<>();
        private final Map<String, Map<String, Object>> docs = new LinkedHashMap<>();
        private final Map<String, String> missing = new LinkedHashMap<>();

        // The explorer can still list a document that is gone (live: an XSLT node whose document was deleted),
        // and a pipeline can name one: a description skips it and says so, rather than failing.
        private String readable(final String uuid) {
            final Optional<XsltDoc> doc = documentAccess.read(new DocRef(XsltDoc.TYPE, uuid), XsltDoc.class);
            if (doc.isEmpty()) {
                missing.put(uuid, NOT_READABLE);
                return null;
            }
            return Objects.requireNonNullElse(doc.get().getData(), "");
        }

        // Stroom resolves an import's href to the XSLT document of that name: read it the same way.
        private String shared(final String href) {
            if (texts.containsKey(href)) {
                return texts.get(href);
            }
            texts.put(href, null);
            for (final DocRef ref : named(href)) {
                final String text = readable(ref.getUuid());
                if (text != null) {
                    texts.put(href, text);
                    final Map<String, Object> doc = new LinkedHashMap<>();
                    doc.put("uuid", ref.getUuid());
                    doc.put("name", href);
                    doc.putAll(SharedXslt.describe(text));
                    docs.put(href, doc);
                    break;
                }
            }
            return texts.get(href);
        }

        private List<DocRef> named(final String href) {
            try {
                return NullSafe.list(docFinderProvider.get().findByName(XsltDoc.TYPE, href)).stream()
                        .filter(documentAccess::canView)
                        .limit(20)
                        .toList();
            } catch (final RuntimeException e) {
                LOGGER.debug("Unable to find the XSLT {}", href, e);
                return List.of();
            }
        }
    }
}
