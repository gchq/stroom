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
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.MergedPipeline.ElementProperty;
import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.ToolArgs;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineLayer;
import stroom.pipeline.shared.data.PipelineLink;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

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
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// What the pipeline write tools (`create_pipeline`, `copy_pipeline`, `update_pipeline`) share, and the indexing
/// and build tools will reuse (the Stroom MCP server's `tools/pipeline_writes.py`): reading a pipeline's layers as
/// the child is written over them, swapping a template's parser, the template's open slots and filling them from
/// the build, reference data entries, property values as Stroom declares their types, and the refusals that keep a
/// new pipeline to its build's own documents and its template's validation.
///
/// Pipelines are read and saved as the user, through Stroom's pipeline service and store, as the pipeline editor
/// reads and saves them.
class PipelineWrites {

    /// Document types a pipeline owns: copied with it, rather than shared with the original.
    static final Set<String> OWNED_TYPES = Set.of(XsltDoc.TYPE, TextConverterDoc.TYPE);
    /// The parser element types.
    static final Set<String> PARSERS = Set.of("XMLParser", "XMLFragmentParser", "JSONParser", "DSParser",
            "CombinedParser");
    /// Elements whose settings are the template's: they validate the pipeline's output.
    static final Set<String> VALIDATION = Set.of("SchemaFilter");

    private static final String SOURCE = "Source";
    private static final String XSLT_FILTER = "XSLTFilter";
    private static final String ADD_ROOT_OBJECT = "addRootObject";
    private static final Pattern LEADING_ACRONYM = Pattern.compile("^[A-Z]+(?=[A-Z][a-z])");
    private static final Pattern UUID_LIKE = Pattern.compile("[0-9a-f-]{36}");
    private static final Pattern TEMPLATE_UUID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern WHOLE_NUMBER = Pattern.compile("-?\\d+");
    private static final Map<String, String> PLAIN = Map.of("boolean", "boolean", "int", "integer",
            "long", "long", "String", "string");

    private final PipelineShapes pipelineShapes;
    private final PipelineDocs pipelineDocs;
    private final PipelinePropertyTypes propertyTypes;
    private final DocumentAccess documentAccess;
    private final Provider<DocFinder> docFinderProvider;
    private final FeedNames feedNames;
    private final ReferenceData referenceData;
    private final WriteGuard guard;
    private final BuildPlan plan;
    private final BuildIndexes buildIndexes;

    /// @param pipelineShapes    Reads a pipeline's layers as Stroom builds them.
    /// @param pipelineDocs      Reads and saves pipelines as the user.
    /// @param propertyTypes     The types Stroom declares for element properties.
    /// @param documentAccess    Reads documents as the user.
    /// @param docFinderProvider Finds documents by name.
    /// @param feedNames         Finds feeds by name.
    /// @param referenceData     Finds the loader for a reference feed.
    /// @param guard             The write guard.
    /// @param plan              The build's plan: its sample's format, a document's build.
    /// @param buildIndexes      The indexes the build writes to.
    @Inject
    PipelineWrites(final PipelineShapes pipelineShapes,
                   final PipelineDocs pipelineDocs,
                   final PipelinePropertyTypes propertyTypes,
                   final DocumentAccess documentAccess,
                   final Provider<DocFinder> docFinderProvider,
                   final FeedNames feedNames,
                   final ReferenceData referenceData,
                   final WriteGuard guard,
                   final BuildPlan plan,
                   final BuildIndexes buildIndexes) {
        this.pipelineShapes = pipelineShapes;
        this.pipelineDocs = pipelineDocs;
        this.propertyTypes = propertyTypes;
        this.documentAccess = documentAccess;
        this.docFinderProvider = docFinderProvider;
        this.feedNames = feedNames;
        this.referenceData = referenceData;
        this.guard = guard;
        this.plan = plan;
        this.buildIndexes = buildIndexes;
    }

    /// @param elementType A pipeline element type.
    /// @return The id Stroom's templates give an element of the type: XMLFragmentParser -> xmlFragmentParser,
    /// DSParser -> dsParser, CombinedParser -> combinedParser (a leading acronym is lowered whole).
    static String elementId(final String elementType) {
        final Matcher acronym = LEADING_ACRONYM.matcher(elementType);
        final String lowered = acronym.find()
                ? acronym.group().toLowerCase() + elementType.substring(acronym.end())
                : elementType;
        return Character.toLowerCase(lowered.charAt(0)) + lowered.substring(1);
    }

    /// @param uuid A pipeline's UUID.
    /// @return Its layers merged, as a child is written over them.
    /// @throws PipelineUnreadableException if Stroom can't load it.
    Layout layout(final String uuid) {
        return Layout.of(pipelineShapes.layers(uuid));
    }

    /// The pipeline data for a child that replaces the template's parser with one of `newType`, linked where the
    /// old one was. A child may remove and add elements as the UI does.
    ///
    /// @param template The template, merged.
    /// @param newType  The parser element type to use.
    /// @return The swap.
    /// @throws ToolRefusal if the type is no parser, or the template has none.
    static Swap swapParser(final Layout template, final String newType) {
        if (!PARSERS.contains(newType)) {
            throw new ToolRefusal("replace_parser must be one of " + PyText.list(sorted(PARSERS)));
        }
        final Map<String, String> types = template.elements();
        final String old = template.chain().stream()
                .filter(id -> PARSERS.contains(types.get(id)))
                .findFirst()
                .orElseThrow(() -> new ToolRefusal("The template has no parser element to replace"));
        final String newId = elementId(newType);
        final List<PipelineLink> outgoing = template.links().stream()
                .filter(link -> link.getFrom().equals(old))
                .toList();
        // And the link into it (Source -> parser): seen, an XMLFragmentParser added with only its outgoing links,
        // which nothing fed; the UI didn't show it, and stepping failed.
        final List<PipelineLink> incoming = template.links().stream()
                .filter(link -> link.getTo().equals(old))
                .toList();
        final List<PipelineElement> added = new ArrayList<>();
        added.add(new PipelineElement(newId, newType));
        List<String> feeds = incoming.stream().map(PipelineLink::getFrom).toList();
        if (incoming.isEmpty()) {
            // A template from before Stroom drew its Source element (Event Data (XML), locally and live) has no
            // link into its parser: the new parser is fed from Source as Stroom's own children write it (seen: the
            // pipeline processed, but the UI showed Source linked to nothing).
            if (!types.containsKey(SOURCE)) {
                added.addFirst(new PipelineElement(SOURCE, SOURCE));
            }
            feeds = List.of(SOURCE);
        }
        final List<PipelineLink> addedLinks = new ArrayList<>();
        feeds.forEach(from -> addedLinks.add(new PipelineLink(from, newId)));
        outgoing.forEach(link -> addedLinks.add(new PipelineLink(newId, link.getTo())));
        final List<PipelineLink> removedLinks = new ArrayList<>(incoming);
        removedLinks.addAll(outgoing);
        return new Swap(List.copyOf(added), new PipelineElement(old, types.get(old)), List.copyOf(addedLinks),
                List.copyOf(removedLinks), newId, types.get(old));
    }

    /// The properties a child must supply for the template's chain to run: the parser's text converter (DSParser,
    /// XMLFragmentParser, CombinedParser) and the first XSLT step's xslt, where the template leaves them unset.
    ///
    /// @param elements      Each element's type, by its id.
    /// @param chain         The element ids in processing order.
    /// @param values        Each property's value, by element and name.
    /// @param replaceParser The parser type to use in place of the template's, or null.
    /// @return The open slots, in chain order (a replacement parser's last).
    static List<Slot> openSlots(final Map<String, String> elements,
                                final List<String> chain,
                                final Map<ElementProperty, Object> values,
                                final String replaceParser) {
        final Map<String, String> types = new LinkedHashMap<>(elements);
        if (replaceParser != null) {
            chain.stream()
                    .filter(id -> PARSERS.contains(types.get(id)))
                    .findFirst()
                    .ifPresent(types::remove);
            types.put(elementId(replaceParser), replaceParser);
        }
        final List<String> order = new ArrayList<>(chain);
        types.keySet().stream()
                .filter(id -> !elements.containsKey(id))
                .forEach(order::add);
        final List<Slot> slots = new ArrayList<>();
        boolean seenXslt = false;
        for (final String element : order) {
            final String type = types.get(element);
            if (type == null) {
                // The parser a replacement takes the place of.
                continue;
            }
            for (final String key : PipelineShapes.KEY_PROPERTIES.getOrDefault(type, List.of())) {
                if ("xslt".equals(key)) {
                    if (seenXslt) {
                        // A second XSLT step (decoration) is optional.
                        continue;
                    }
                    seenXslt = true;
                }
                final Object value = values.get(new ElementProperty(element, key));
                if (("xslt".equals(key) || "textConverter".equals(key)) && (value == null || "".equals(value))) {
                    slots.add(new Slot(element, type, key));
                }
            }
        }
        return slots;
    }

    /// The properties with the template's open slots filled from the build's own documents, where there is
    /// exactly one candidate. A parser with no converter in the build is refused: the pipeline could not parse
    /// anything.
    ///
    /// @param build         The build.
    /// @param template      The template, merged.
    /// @param replaceParser The parser type to use in place of the template's, or null.
    /// @param properties    The properties given.
    /// @return The properties, what was filled, and what is still open.
    /// @throws ToolRefusal if a converter is needed and the build has none, or a slot has several candidates.
    Filled fillOpenSlots(final String build,
                         final Layout template,
                         final String replaceParser,
                         final List<PropertyValue> properties) {
        return fillOpenSlots(build, template, replaceParser, properties, null);
    }

    /// As [#fillOpenSlots(String, Layout, String, List)], for a new version: with no converter of its own, it reads
    /// the feed with the one the pipeline it follows runs.
    ///
    /// @param build         The build.
    /// @param template      The template, merged.
    /// @param replaceParser The parser type to use in place of the template's, or null.
    /// @param properties    The properties given.
    /// @param previous      The pipeline the new one is the next version of, or null for a new source's.
    /// @return The properties, what was filled, and what is still open.
    /// @throws ToolRefusal if a converter is needed and neither the build nor the previous version has one, or a
    /// slot has several candidates.
    Filled fillOpenSlots(final String build,
                         final Layout template,
                         final String replaceParser,
                         final List<PropertyValue> properties,
                         final PipelineDoc previous) {
        final Map<String, DocRef> prior = new LinkedHashMap<>();
        ownProperties(previous).forEach(property -> {
            final DocRef entity = NullSafe.get(property.getValue(), PipelinePropertyValue::getEntity);
            if (entity != null) {
                prior.put(property.getElement() + "." + property.getName(), entity);
            }
        });
        final Set<String> given = new HashSet<>();
        properties.forEach(property -> given.add(property.dotted()));
        final List<WriteGuard.BuildDoc> docs = guard.folderContents(build);
        final List<PropertyValue> out = new ArrayList<>(properties);
        final List<String> filled = new ArrayList<>();
        final List<String> stillOpen = new ArrayList<>();
        for (final Slot slot : openSlots(template.elements(), template.chain(), template.properties(),
                replaceParser)) {
            if (given.contains(slot.dotted())) {
                continue;
            }
            final List<WriteGuard.BuildDoc> candidates;
            final String docType;
            if ("textConverter".equals(slot.property())) {
                candidates = docs.stream().filter(doc -> TextConverterDoc.TYPE.equals(doc.type())).toList();
                final DocRef was = prior.get(slot.element() + ".textConverter");
                if (candidates.isEmpty() && was != null && TextConverterDoc.TYPE.equals(was.getType())) {
                    out.add(PropertyValue.document(slot.element(), "textConverter", was.getUuid(),
                            TextConverterDoc.TYPE));
                    filled.add(slot.element() + ".textConverter = " + was.getName() + " (as " + previous.getName()
                               + " has it)");
                    continue;
                }
                if (candidates.isEmpty()) {
                    throw new ToolRefusal("The template's " + slot.type() + " (" + slot.element() + ") needs a "
                                          + "text converter and build '" + build + "' has none: "
                                          + "build_data_splitter with the sample (save_as=<name>), or "
                                          + "save_text_converter, then create the pipeline (or pass "
                                          + slot.element() + ".textConverter in set_properties).");
                }
                docType = TextConverterDoc.TYPE;
            } else {
                final List<WriteGuard.BuildDoc> xslts = docs.stream()
                        .filter(doc -> XsltDoc.TYPE.equals(doc.type()))
                        .toList();
                final List<WriteGuard.BuildDoc> withMapping = xslts.stream()
                        .filter(doc -> translationMapping(doc.uuid()).isPresent())
                        .toList();
                candidates = withMapping.isEmpty()
                        ? xslts
                        : withMapping;
                docType = XsltDoc.TYPE;
                if (candidates.isEmpty()) {
                    stillOpen.add(slot.element() + ".xslt");
                    continue;
                }
            }
            if (candidates.size() > 1) {
                throw new ToolRefusal(slot.dotted() + " is not set and build '" + build + "' has "
                                      + candidates.size() + " " + docType + " documents ("
                                      + String.join(", ", candidates.stream()
                        .map(doc -> doc.name() + " (" + doc.uuid() + ")")
                        .toList()) + "): pass the right one in set_properties.");
            }
            final WriteGuard.BuildDoc chosen = candidates.getFirst();
            out.add(PropertyValue.document(slot.element(), slot.property(), chosen.uuid(), docType));
            filled.add(slot.dotted() + " = " + chosen.name() + " (the build's only " + docType + ")");
        }
        return new Filled(out, filled, stillOpen);
    }

    /// The XSLT and text converter a child supplies must be the build's own (made with save_xslt /
    /// save_text_converter), not another source's or a library's: those are inherited from the template.
    ///
    /// @param build      The build.
    /// @param properties The properties the child sets.
    /// @param allowed    True when the user said an existing document is the right one.
    /// @throws ToolRefusal if one isn't the build's.
    void ownDocuments(final String build, final List<PropertyValue> properties, final boolean allowed) {
        ownDocuments(build, properties, allowed, null);
    }

    /// As [#ownDocuments(String, List, boolean)], for a new version, which may run the text converter the pipeline
    /// it follows runs.
    ///
    /// @param build      The build.
    /// @param properties The properties the child sets.
    /// @param allowed    True when the user said an existing document is the right one.
    /// @param previous   The pipeline the new one is the next version of, or null for a new source's.
    /// @throws ToolRefusal if one isn't the build's.
    void ownDocuments(final String build,
                      final List<PropertyValue> properties,
                      final boolean allowed,
                      final PipelineDoc previous) {
        if (allowed) {
            return;
        }
        final Set<String> prior = new HashSet<>();
        ownProperties(previous).stream()
                .filter(property -> "textConverter".equals(property.getName()))
                .map(property -> NullSafe.get(property.getValue(), PipelinePropertyValue::getEntity, DocRef::getUuid))
                .filter(Objects::nonNull)
                .forEach(prior::add);
        for (final PropertyValue property : properties) {
            if (!MergedPipeline.CODE_PROPERTIES.containsKey(property.name()) || property.docUuid() == null) {
                continue;
            }
            if ("textConverter".equals(property.name()) && prior.contains(property.docUuid())) {
                continue;
            }
            final DocRef ref = new DocRef(property.docType(), property.docUuid());
            if (!guard.tags(ref).contains(WriteGuard.MANAGED) || !guard.buildOf(ref).equals(Optional.of(build))) {
                throw new ToolRefusal(property.dotted() + ": " + property.docType() + " " + property.docUuid()
                                      + " is not a document of build '" + build + "'. A new pipeline's "
                                      + "translation is written for its own source: build_translation_xslt "
                                      + "from a mapping, saved in the build (build=, name=), and a text converter "
                                      + "with build_data_splitter and save_text_converter. Shared libraries are "
                                      + "xsl:imported or inherited from the template, not set on the child. If "
                                      + "the user says this existing document is the right one, call again with "
                                      + "reuse_existing_docs=true.");
            }
        }
    }

    /// The template's parser (or the replacement) must read the format of the build's sample streams.
    ///
    /// @param build         The build.
    /// @param template      The template, merged.
    /// @param replaceParser The parser type to use in place of the template's, or null.
    /// @param allowed       True when the user insists on the template.
    /// @throws ToolRefusal if the parser can't read the sample.
    void parserReadsSample(final String build,
                           final Layout template,
                           final String replaceParser,
                           final boolean allowed) {
        if (allowed) {
            return;
        }
        final Map<String, String> types = template.elements();
        final String parser = replaceParser != null
                ? replaceParser
                : template.chain().stream()
                        .map(types::get)
                        .filter(PARSERS::contains)
                        .findFirst()
                        .orElse(null);
        if (parser == null) {
            return;
        }
        final Optional<Map<String, Object>> sample = plan.sampleFormat(build);
        if (sample.isEmpty()) {
            return;
        }
        final Map<String, Object> format = sample.get();
        final List<String> readers = BuildPlan.PARSER_FOR_FORMAT.get(String.valueOf(format.get("format")));
        if (!NullSafe.isEmptyCollection(readers) && !readers.contains(parser)) {
            throw new ToolRefusal("The build's sample (stream " + format.get("stream_id") + " of feed "
                                  + format.get("feed") + ") is " + format.get("format") + ", which this template's "
                                  + parser + " cannot read; it needs " + readers.getFirst() + " ("
                                  + format.get("suggested_parser") + "). Choose the template with that parser "
                                  + "(find_pipeline_templates stage=translation), or replace_parser for XML "
                                  + "fragments. If the user insists on this template, call again with "
                                  + "accept_parser_mismatch=true.");
        }
    }

    /// `jsonParser.addRootObject` false when the build's sample is a JSON array, or the translation XSLT's
    /// mapping reads one, unless set already: with the parser's root map, the whole array is one record. Seen: four
    /// 755 KB arrays stepped as four records of 985 events each, every step outlasting Stroom's wait, until
    /// stepping failed; and then, with no mapping (the XSLT written by hand), never set at all.
    ///
    /// @param elements      Each element's type, by its id, in the pipeline's order.
    /// @param properties    The properties set.
    /// @param sampleIsArray True if the build's sample is a JSON array.
    /// @return The property to add, or empty.
    Optional<PropertyValue> jsonArrayParser(final Map<String, String> elements,
                                            final List<PropertyValue> properties,
                                            final boolean sampleIsArray) {
        final Optional<String> parser = elements.entrySet().stream()
                .filter(entry -> "JSONParser".equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst();
        if (parser.isEmpty() || properties.stream().anyMatch(property ->
                parser.get().equals(property.element()) && ADD_ROOT_OBJECT.equals(property.name()))) {
            return Optional.empty();
        }
        final PropertyValue eachItem = PropertyValue.plain(parser.get(), ADD_ROOT_OBJECT, false);
        if (sampleIsArray) {
            return Optional.of(eachItem);
        }
        final Optional<PropertyValue> xslt = properties.stream()
                .filter(property -> "xslt".equals(property.name()) && property.docUuid() != null)
                .findFirst();
        if (xslt.isEmpty()) {
            return Optional.empty();
        }
        final Map<?, ?> mapping = translationMapping(xslt.get().docUuid())
                .map(payload -> payload.get("mapping") instanceof final Map<?, ?> map
                        ? map
                        : null)
                .orElse(null);
        if (mapping != null && "json".equals(mapping.get("input"))
            && "array".equals(Objects.requireNonNullElse(mapping.get("json_layout"), "array"))) {
            return Optional.of(eachItem);
        }
        return Optional.empty();
    }

    /// @param build The build, or null.
    /// @return Whether the build's sample stream is a JSON array (each item a record, read with addRootObject
    /// false); false when it can't be told (a convenience on top: never fail the write over it).
    boolean sampleIsArray(final String build) {
        if (NullSafe.isBlankString(build)) {
            return false;
        }
        try {
            return plan.sampleFormat(build)
                    .map(format -> "json array".equals(format.get("format")))
                    .orElse(false);
        } catch (final RuntimeException e) {
            return false;
        }
    }

    /// Each plain value as the type Stroom declares for its element's property, before anything is written: a
    /// string "false" for a boolean is false. Seen: jsonParser.addRootObject written as {'string': 'false'}, and
    /// every step of that pipeline, on any stream, outlasted Stroom's wait. A property the element doesn't have, or
    /// a value that isn't of its type, is refused.
    ///
    /// @param types      Each element's type, by its id.
    /// @param properties The properties.
    /// @return The properties, their values as declared, and the value key ([#value]) for each plain one.
    /// @throws ToolRefusal if one isn't a property of its element, or its value isn't of its type.
    Typed typedProperties(final Map<String, String> types, final List<PropertyValue> properties) {
        final Map<String, Map<String, String>> declared = propertyTypes.declared();
        final List<PropertyValue> typed = new ArrayList<>();
        final Map<String, String> keys = new LinkedHashMap<>();
        for (final PropertyValue property : properties) {
            final String elementType = types.get(property.element());
            if (declared.isEmpty() || elementType == null || property.docUuid() != null) {
                typed.add(property);
                continue;
            }
            final Map<String, String> ofType = declared.getOrDefault(elementType, Map.of());
            final String kind = ofType.get(property.name());
            if (kind == null) {
                throw new ToolRefusal(property.element() + " (" + elementType + ") has no property '"
                                      + property.name() + "'; its properties are "
                                      + PyText.list(sorted(ofType.keySet())));
            }
            if (!PLAIN.containsKey(kind)) {
                // A document: doc_uuid and doc_type, refused by value() without them.
                typed.add(property);
                continue;
            }
            typed.add(property.withValue(asDeclared(property, kind)));
            keys.put(property.dotted(), PLAIN.get(kind));
        }
        return new Typed(typed, keys);
    }

    /// @param property A property.
    /// @param key      Stroom's value key for it ([#typedProperties]), or null.
    /// @return Its value as Stroom stores it.
    /// @throws ToolRefusal if it has neither a document nor a value, or its document can't be found.
    PipelinePropertyValue value(final PropertyValue property, final String key) {
        if (property.docUuid() != null) {
            if (property.docType() == null) {
                throw new ToolRefusal(property.dotted() + ": give doc_type with doc_uuid");
            }
            final DocRef doc = documentAccess.findDocRef(property.docUuid())
                    .filter(found -> property.docType().equals(found.getType()))
                    .orElseThrow(() -> new ToolRefusal(property.dotted() + ": no " + property.docType() + " "
                                                       + property.docUuid()));
            return new PipelinePropertyValue(new DocRef(property.docType(), property.docUuid(), doc.getName()));
        }
        final Object given = property.value();
        if (given == null) {
            throw new ToolRefusal(property.dotted() + ": give value or doc_uuid");
        }
        if (key != null) {
            // As Stroom declares the property (typedProperties).
            return switch (key) {
                case "boolean" -> new PipelinePropertyValue((Boolean) given);
                case "integer" -> new PipelinePropertyValue(integer(property));
                case "long" -> new PipelinePropertyValue(((Number) given).longValue());
                default -> new PipelinePropertyValue(String.valueOf(given));
            };
        }
        if (given instanceof final Boolean bool) {
            return new PipelinePropertyValue(bool);
        }
        if (given instanceof final Number number) {
            return number.longValue() == number.intValue()
                    ? new PipelinePropertyValue(number.intValue())
                    : new PipelinePropertyValue(number.longValue());
        }
        return new PipelinePropertyValue(String.valueOf(given));
    }

    /// A template given as a UUID or as its exact name.
    ///
    /// @param template The template's UUID or name, or null.
    /// @return Its UUID.
    /// @throws ToolRefusal if none is given, or no one pipeline has the name.
    String templateUuid(final String template) {
        if (NullSafe.isBlankString(template)) {
            throw new ToolRefusal("Give template_uuid (or template: the template's UUID or name), from "
                                  + "find_pipeline_templates stage=translation");
        }
        if (TEMPLATE_UUID.matcher(template).matches()) {
            return template;
        }
        return docRefByName(PipelineDoc.TYPE, template).getUuid();
    }

    /// @param type A document type.
    /// @param name A document's name (a feed's, or for other types its name or UUID).
    /// @return The one document of the type with that name, as the user can view it.
    /// @throws ToolRefusal if there is none, or several.
    DocRef docRefByName(final String type, final String name) {
        if (FeedDoc.TYPE.equals(type)) {
            final DocRef found = feedNames.exact(name)
                    .orElseThrow(() -> FeedNames.noFeedNamed(name));
            return new DocRef(FeedDoc.TYPE, found.getUuid(), NullSafe.nonBlankStringElse(found.getName(), name));
        }
        if (UUID_LIKE.matcher(name).matches()) {
            return documentAccess.findDocRef(name)
                    .filter(found -> type.equals(found.getType()))
                    .orElseThrow(() -> new ToolRefusal("No " + type + " with UUID " + name));
        }
        final List<DocRef> matches = NullSafe.list(docFinderProvider.get().findByName(type, name)).stream()
                .filter(documentAccess::canView)
                .filter(found -> type.equals(found.getType()) && name.equals(found.getName()))
                .limit(20)
                .toList();
        if (matches.size() != 1) {
            throw new ToolRefusal((matches.isEmpty()
                    ? "No"
                    : String.valueOf(matches.size())) + " " + type + " document(s) named '" + name + "'"
                                  + (matches.size() > 1
                    ? "; give the UUID"
                    : " (find_reference_data lists loaders)"));
        }
        return matches.getFirst();
    }

    /// @param pipeline   The pipeline, merged.
    /// @param references The reference data to attach.
    /// @return Its pipeline references: on the element asked for, or the first XSLT step.
    /// @throws ToolRefusal if the pipeline has no XSLT step, or a feed or loader can't be found.
    List<PipelineReference> referenceEntries(final Layout pipeline, final List<ReferenceRequest> references) {
        final List<String> xsltSteps = pipeline.chain().stream()
                .filter(id -> XSLT_FILTER.equals(pipeline.elements().get(id)))
                .toList();
        final List<PipelineReference> out = new ArrayList<>();
        for (final ReferenceRequest reference : references) {
            final String element = reference.element() != null
                    ? reference.element()
                    : (xsltSteps.isEmpty()
                            ? null
                            : xsltSteps.getFirst());
            if (element == null) {
                throw new ToolRefusal("The pipeline has no XSLT step to attach reference data to");
            }
            final String loader = reference.loaderPipeline() != null
                    ? reference.loaderPipeline()
                    : referenceData.resolveLoader(reference.feed());
            out.add(new PipelineReference(element, "pipelineReference", docRefByName(PipelineDoc.TYPE, loader),
                    docRefByName(FeedDoc.TYPE, reference.feed()), "Reference"));
        }
        return out;
    }

    /// Refuses a property on a schema filter: validation is the template's, there to catch output that is wrong.
    /// When it fails, the output is what to fix (e.g. a root element that does not say which schema it follows).
    ///
    /// @param types      Each element's type, by its id.
    /// @param properties The properties to set.
    /// @throws ToolRefusal if one is on a schema filter.
    static void keepValidation(final Map<String, String> types, final List<PropertyValue> properties) {
        final List<String> touched = sorted(properties.stream()
                .map(PropertyValue::element)
                .filter(element -> VALIDATION.contains(types.get(element)))
                .toList());
        if (!touched.isEmpty()) {
            throw new ToolRefusal(PyText.list(touched) + " validate the pipeline's output against "
                                  + "its schema; their settings are not changed here. Fix the output instead: an "
                                  + "Elasticsearch indexing XSLT's root must say which JSON schema it follows "
                                  + "(<array xsi:schemaLocation=\"http://www.w3.org/2005/xpath-functions "
                                  + "file://xpath-functions.xsd\">), which save_xslt index_plan=... writes; an "
                                  + "events XSLT must produce valid event-logging. If the template's validation "
                                  + "itself is wrong, that is for the template's owner to change.");
        }
    }

    /// Sets one element property on a pipeline the tools created, in its build, e.g. `schemaFilter.schemaGroup`
    /// (the MCP server's `set_pipeline_property`).
    ///
    /// @param context      The call's context.
    /// @param pipelineUuid The pipeline.
    /// @param property     The property to set.
    /// @return `type`, `uuid`, `name` and `set` (`element.name`).
    /// @throws ToolRefusal if the pipeline isn't the tools' own in a build, has no such element, or the property
    ///                     can't be set.
    Map<String, Object> setPipelineProperty(final ToolCallContext context,
                                            final String pipelineUuid,
                                            final PropertyValue property) {
        final PipelineDoc doc = pipelineDocs.read(pipelineUuid);
        final String build = guard.checkInBuild(context.session(),
                new DocRef(PipelineDoc.TYPE, pipelineUuid, doc.getName()));
        final Map<String, String> types = layout(pipelineUuid).elements();
        if (!types.containsKey(property.element())) {
            throw new ToolRefusal("No element '" + property.element() + "' in this pipeline; elements are "
                                  + PyText.list(sorted(types.keySet())));
        }
        keepValidation(types, List.of(property));
        if ("ElasticIndexingFilter".equals(types.get(property.element())) && "indexName".equals(property.name())
            && property.value() instanceof final String indexName) {
            final Optional<String> other = buildIndexes.otherIndex(build, indexName, pipelineUuid);
            if (other.isPresent()) {
                throw new ToolRefusal(other.get() + " rename_index changes the index name in both, and the index "
                                      + "template follows.");
            }
        }
        final Typed typed = typedProperties(types, List.of(property));
        final PropertyValue set = typed.properties().getFirst();
        final PipelineDoc saved = pipelineDocs.write(PipelineDocs.withProperty(doc, set.element(), set.name(),
                value(set, typed.keys().get(set.dotted()))));
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", PipelineDoc.TYPE);
        result.put("uuid", saved.getUuid());
        result.put("name", saved.getName());
        result.put("set", set.dotted());
        return result;
    }

    /// Attaches reference data to a pipeline the tools created, so its XSLT's stroom:lookup() calls find the maps
    /// (the MCP server's `set_pipeline_references`): added to any it has already.
    ///
    /// @param context      The call's context.
    /// @param pipelineUuid The pipeline.
    /// @param references   The reference data to attach.
    /// @return `type`, `uuid`, `name`, `reference_data` (all it has now) and `added` (how many were new).
    /// @throws ToolRefusal if the pipeline isn't the tools' own in a build, or a reference can't be made.
    Map<String, Object> setPipelineReferences(final ToolCallContext context,
                                              final String pipelineUuid,
                                              final List<ReferenceRequest> references) {
        final PipelineDoc doc = pipelineDocs.read(pipelineUuid);
        guard.checkInBuild(context.session(), new DocRef(PipelineDoc.TYPE, pipelineUuid, doc.getName()));
        final List<PipelineReference> wanted = referenceEntries(layout(pipelineUuid), references);
        final PipelineData data = doc.getPipelineData();
        final List<PipelineReference> existing = new ArrayList<>(NullSafe.list(NullSafe.get(data,
                PipelineData::getAddedPipelineReferences)));
        final Set<String> have = new HashSet<>();
        existing.forEach(reference -> have.add(referenceKey(reference)));
        final List<PipelineReference> added = wanted.stream()
                .filter(reference -> !have.contains(referenceKey(reference)))
                .toList();
        final PipelineDataBuilder builder = data == null
                ? new PipelineDataBuilder()
                : new PipelineDataBuilder(data);
        builder.addPipelineReferences(added);
        existing.addAll(added);
        final PipelineDoc saved = pipelineDocs.write(doc.copy().pipelineData(builder.build()).build());
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", PipelineDoc.TYPE);
        result.put("uuid", saved.getUuid());
        result.put("name", saved.getName());
        result.put("reference_data", existing.stream().map(PipelineWrites::describe).toList());
        result.put("added", added.size());
        return result;
    }

    /// The previous version's own plain settings (addRootObject false, say: not documents) on elements the new one
    /// has, that nothing sets already: a new version is configured as the one it follows.
    ///
    /// @param previous   The pipeline the new one is the next version of.
    /// @param types      The new one's element types, by id.
    /// @param properties What the new one sets already.
    /// @return The settings to carry over.
    static List<PropertyValue> plainSettings(final PipelineDoc previous,
                                             final Map<String, String> types,
                                             final List<PropertyValue> properties) {
        final Set<String> setting = new HashSet<>();
        properties.forEach(property -> setting.add(property.dotted()));
        final List<PropertyValue> out = new ArrayList<>();
        for (final PipelineProperty property : ownProperties(previous)) {
            final PipelinePropertyValue held = property.getValue();
            final Object scalar = scalar(held);
            if (types.containsKey(property.getElement())
                && !setting.contains(property.getElement() + "." + property.getName())
                && scalar != null && held.getEntity() == null) {
                out.add(PropertyValue.plain(property.getElement(), property.getName(), scalar));
            }
        }
        return out;
    }

    /// The previous version's reference data for the new one, on the same element (the template's first XSLT step
    /// if the new one has no element of that id), less those `references` gives already.
    ///
    /// @param template The new one's template, merged.
    /// @param carried  The reference data the previous version loads itself.
    /// @param given    Those the call gives.
    /// @return The references to add, each once.
    /// @throws ToolRefusal if the template has no XSLT step to attach them to.
    static List<PipelineReference> carriedReferences(final Layout template,
                                                     final List<PipelineReference> carried,
                                                     final List<PipelineReference> given) {
        final Map<String, String> types = template.elements();
        final List<String> steps = template.chain().stream()
                .filter(id -> XSLT_FILTER.equals(types.get(id)))
                .toList();
        final Set<String> seen = new HashSet<>();
        given.forEach(reference -> seen.add(referenceKey(reference)));
        final List<PipelineReference> out = new ArrayList<>();
        for (final PipelineReference reference : carried) {
            final String element = types.containsKey(reference.getElement())
                    ? reference.getElement()
                    : (steps.isEmpty()
                            ? null
                            : steps.getFirst());
            if (element == null) {
                throw new ToolRefusal("The template has no XSLT step to attach the previous version's reference "
                                      + "data to");
            }
            final PipelineReference moved = new PipelineReference(element,
                    NullSafe.nonBlankStringElse(reference.getName(), "pipelineReference"), reference.getPipeline(),
                    reference.getFeed(), NullSafe.nonBlankStringElse(reference.getStreamType(), "Reference"));
            if (seen.add(referenceKey(moved))) {
                out.add(moved);
            }
        }
        return out;
    }

    /// What the previous version sets itself that the new one doesn't (it is its template's child, with the build's
    /// documents): shown to the user, who may want one set too (update_pipeline), or copy_pipeline instead.
    ///
    /// @param previous   The pipeline the new one is the next version of.
    /// @param properties What the new one sets.
    /// @return Each property as `element.name`, then each element it adds or removes.
    static List<String> notCarried(final PipelineDoc previous, final List<PropertyValue> properties) {
        final Set<String> setting = new HashSet<>();
        properties.forEach(property -> setting.add(property.dotted()));
        final List<String> out = new ArrayList<>();
        ownProperties(previous).stream()
                .map(property -> property.getElement() + "." + property.getName())
                .filter(dotted -> !setting.contains(dotted))
                .forEach(out::add);
        final PipelineData data = previous.getPipelineData();
        if (data != null) {
            NullSafe.list(data.getAddedElements()).forEach(element -> out.add("element " + element.getId() + " ("
                                                                              + element.getType() + ") added"));
            NullSafe.list(data.getRemovedElements()).forEach(element -> out.add("element " + element.getId()
                                                                                + " removed"));
        }
        return out;
    }

    /// @param reference A pipeline reference.
    /// @return It in words: `<feed> via <loader pipeline> on <element>`.
    static String describe(final PipelineReference reference) {
        return MergedPipeline.describe(reference) + " on " + reference.getElement();
    }

    /// @param values Some names.
    /// @return Them sorted, each once.
    static List<String> sorted(final Collection<String> values) {
        return List.copyOf(new TreeSet<>(values));
    }

    // The mapping a translation XSLT keeps in its description, if it is one the user may read.
    private Optional<Map<String, Object>> translationMapping(final String xsltUuid) {
        return documentAccess.read(new DocRef(XsltDoc.TYPE, xsltUuid), XsltDoc.class)
                .flatMap(xslt -> KeptMapping.readMapping(xslt.getDescription()))
                .filter(kept -> "translation".equals(kept.kind()))
                .map(kept -> NullSafe.map(kept.payload()));
    }

    // A plain value as the type Stroom declares for it.
    // The properties a pipeline's own layer adds; none for no pipeline.
    private static List<PipelineProperty> ownProperties(final PipelineDoc pipeline) {
        return pipeline == null
                ? List.of()
                : PipelineDocs.addedProperties(pipeline);
    }

    // A plain value: its boolean, integer, long or string, as Stroom holds it; null for a document or none.
    private static Object scalar(final PipelinePropertyValue held) {
        if (held == null) {
            return null;
        }
        if (held.getBoolean() != null) {
            return held.getBoolean();
        }
        if (held.getInteger() != null) {
            return held.getInteger();
        }
        if (held.getLong() != null) {
            return held.getLong();
        }
        return held.getString();
    }

    private static Object asDeclared(final PropertyValue property, final String kind) {
        Object value = property.value();
        switch (kind) {
            case "boolean" -> {
                if (value instanceof final String text
                    && ("true".equalsIgnoreCase(text.strip()) || "false".equalsIgnoreCase(text.strip()))) {
                    value = "true".equalsIgnoreCase(text.strip());
                }
                if (!(value instanceof Boolean)) {
                    throw new ToolRefusal(property.dotted() + " is true or false, not "
                                          + PyText.repr(property.value()));
                }
            }
            case "int", "long" -> {
                if (value instanceof final String text && WHOLE_NUMBER.matcher(text.strip()).matches()) {
                    try {
                        value = Long.parseLong(text.strip());
                    } catch (final NumberFormatException e) {
                        value = null;
                    }
                }
                if (!(value instanceof Long) && !(value instanceof Integer)) {
                    throw new ToolRefusal(property.dotted() + " is a whole number, not "
                                          + PyText.repr(property.value()));
                }
                value = ((Number) value).longValue();
            }
            default -> {
                if (value instanceof final Boolean bool) {
                    value = bool.toString();
                } else if (value != null && !(value instanceof String)) {
                    value = String.valueOf(value);
                }
            }
        }
        return value;
    }

    private static Integer integer(final PropertyValue property) {
        try {
            return Math.toIntExact(((Number) property.value()).longValue());
        } catch (final ArithmeticException e) {
            throw new ToolRefusal(property.dotted() + " is a whole number, not " + PyText.repr(property.value()));
        }
    }

    private static String referenceKey(final PipelineReference reference) {
        return reference.getElement() + "\n" + NullSafe.get(reference.getFeed(), DocRef::getUuid) + "\n"
               + NullSafe.get(reference.getPipeline(), DocRef::getUuid);
    }


    // --------------------------------------------------------------------------------


    /// A pipeline's layers merged as a child is written over them (the MCP server's `merge_layers`): its elements
    /// and links as the layers add and remove them, `Source` included where a layer adds it (Stroom adds one to a
    /// template that doesn't, which a child mustn't write again), and its properties as Stroom merges them.
    ///
    /// @param elements   Each element's type by its id, in the order the layers add them.
    /// @param links      Its links, between elements it has.
    /// @param properties Each property's value by its element and name ([MergedPipeline#properties()]).
    record Layout(Map<String, String> elements, List<PipelineLink> links, Map<ElementProperty, Object> properties) {

        /// @param layers A pipeline's layers, root template first.
        /// @return The pipeline, merged.
        /// @throws PipelineUnreadableException if Stroom can't merge them.
        static Layout of(final List<PipelineLayer> layers) {
            final Map<String, String> elements = new LinkedHashMap<>();
            final List<PipelineLink> links = new ArrayList<>();
            for (final PipelineLayer layer : NullSafe.list(layers)) {
                final PipelineData data = NullSafe.get(layer, PipelineLayer::getPipelineData);
                if (data == null) {
                    continue;
                }
                // An entry missing what it needs is passed over, as Stroom passes it over.
                NullSafe.list(data.getRemovedElements()).forEach(element -> elements.remove(element.getId()));
                for (final PipelineElement element : NullSafe.list(data.getAddedElements())) {
                    if (NullSafe.isNonBlankString(element.getId()) && NullSafe.isNonBlankString(element.getType())) {
                        elements.put(element.getId(), element.getType());
                    }
                }
                NullSafe.list(data.getRemovedLinks()).forEach(links::remove);
                for (final PipelineLink link : NullSafe.list(data.getAddedLinks())) {
                    if (NullSafe.isNonBlankString(link.getFrom()) && NullSafe.isNonBlankString(link.getTo())
                        && !links.contains(link)) {
                        links.add(new PipelineLink(link.getFrom(), link.getTo()));
                    }
                }
            }
            // Links whose ends no longer exist are dropped, as Stroom does.
            links.removeIf(link -> !elements.containsKey(link.getFrom()) || !elements.containsKey(link.getTo()));
            final Map<ElementProperty, Object> properties;
            try {
                properties = MergedPipeline.of(layers).properties();
            } catch (final RuntimeException e) {
                throw new PipelineUnreadableException(PipelineShapes.reason(e));
            }
            return new Layout(elements, List.copyOf(links), properties);
        }

        /// @return The element ids in processing order, following links from each element nothing feeds into.
        List<String> chain() {
            final Set<String> targets = new HashSet<>();
            final Map<String, List<String>> children = new LinkedHashMap<>();
            for (final PipelineLink link : links) {
                targets.add(link.getTo());
                children.computeIfAbsent(link.getFrom(), k -> new ArrayList<>()).add(link.getTo());
            }
            final List<String> stack = new ArrayList<>(elements.keySet().stream()
                    .filter(id -> !targets.contains(id))
                    .toList()
                    .reversed());
            final Set<String> order = new LinkedHashSet<>();
            while (!stack.isEmpty()) {
                final String current = stack.removeLast();
                if (order.add(current)) {
                    stack.addAll(children.getOrDefault(current, List.of()).reversed());
                }
            }
            return List.copyOf(order);
        }
    }

    /// A template's parser swapped for another (the MCP server's `swap_parser`).
    ///
    /// @param addedElements The elements the child adds: the new parser (after a `Source`, where the template has
    ///                      none).
    /// @param removed       The template's parser, which the child removes.
    /// @param addedLinks    The links the child adds: into the new parser, and on from it.
    /// @param removedLinks  The template's links into and out of its parser, which the child removes.
    /// @param newId         The new parser's element id.
    /// @param oldType       The template parser's type.
    record Swap(List<PipelineElement> addedElements,
                PipelineElement removed,
                List<PipelineLink> addedLinks,
                List<PipelineLink> removedLinks,
                String newId,
                String oldType) {

        /// @param builder A child's pipeline data.
        /// @return It, with the swap.
        PipelineDataBuilder applyTo(final PipelineDataBuilder builder) {
            builder.addElements(addedElements);
            builder.removeElement(removed);
            builder.addLinks(addedLinks);
            builder.removeLinks(removedLinks);
            return builder;
        }
    }

    /// A property a child must supply.
    ///
    /// @param element  The element's id.
    /// @param type     Its type.
    /// @param property The property's name.
    record Slot(String element, String type, String property) {

        /// @return It as `element.property`.
        String dotted() {
            return element + "." + property;
        }
    }

    /// The properties with a template's open slots filled.
    ///
    /// @param properties The properties: those given, then those filled.
    /// @param filled     What was filled, in words.
    /// @param stillOpen  What is still open, as `element.xslt`.
    record Filled(List<PropertyValue> properties, List<String> filled, List<String> stillOpen) {

    }

    /// Properties with their values as Stroom declares them.
    ///
    /// @param properties The properties.
    /// @param keys       Stroom's value key (`boolean`, `integer`, `long`, `string`) for each plain one, by
    ///                   `element.name`.
    record Typed(List<PropertyValue> properties, Map<String, String> keys) {

    }

    /// Reference data an XSLT step reads with stroom:lookup(): a feed of Reference streams, loaded by a loader
    /// pipeline (the MCP server's `PipelineReference`).
    ///
    /// @param feed           The reference feed's name.
    /// @param loaderPipeline The loader pipeline's name or UUID, or null for the one other pipelines load the feed
    ///                       with, else the environment's only loader.
    /// @param element        The XSLT element that does the lookups, or null for the pipeline's translation step.
    record ReferenceRequest(String feed, String loaderPipeline, String element) {

        /// How a reference's arguments are described.
        static final String FEED = "The reference feed's name.";
        static final String LOADER_PIPELINE = "The loader pipeline's name or UUID. Left out: the one other "
                                              + "pipelines already load this feed with, else the environment's "
                                              + "only loader (find_reference_data lists them); no name is assumed.";
        static final String ELEMENT = "The XSLT element that does the lookups; defaults to the pipeline's "
                                      + "translation step.";

        /// Reads the references a call gives: a list, one reference, the list as JSON text, or a feed's name.
        ///
        /// @param given    The argument, as the call's JSON gives it, or null.
        /// @param argument The argument's name, for a refusal.
        /// @return The references.
        /// @throws ToolRefusal if one has no feed.
        static List<ReferenceRequest> listOf(final Object given, final String argument) {
            final List<Object> items = ToolArgs.oneOrMore(given);
            final List<ReferenceRequest> references = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                final Object item = items.get(i);
                if (item instanceof final String feed) {
                    references.add(new ReferenceRequest(feed, null, null));
                    continue;
                }
                final Map<?, ?> map = item instanceof final Map<?, ?> found
                        ? found
                        : Map.of();
                final String feed = text(map.get("feed"));
                if (feed == null) {
                    throw new ToolRefusal(argument + "[" + i + "].feed is required: a reference is {feed, "
                                          + "loader_pipeline, element}, from find_reference_data");
                }
                references.add(new ReferenceRequest(feed, text(map.get("loader_pipeline")),
                        text(map.get("element"))));
            }
            return references;
        }

        private static String text(final Object value) {
            return value == null || "".equals(value)
                    ? null
                    : String.valueOf(value);
        }
    }
}
