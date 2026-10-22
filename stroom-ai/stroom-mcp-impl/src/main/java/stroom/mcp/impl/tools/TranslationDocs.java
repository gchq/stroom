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

import stroom.ai.impl.tools.AgentKnowledge;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.cef.CefPlan;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.indexing.EcsSchema;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.validation.XmlTrees;
import stroom.ai.impl.tools.validation.XsltChecks;
import stroom.ai.impl.tools.validation.XsltSafety;
import stroom.ai.impl.tools.validation.XsltVersionHistory;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.shared.AbstractDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.w3c.dom.Element;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/// The translation documents of a build, created and saved through Stroom's own stores as the user: text
/// converters, XSLTs and dictionaries (the Stroom MCP server's `tools/translation.py` `create_text_converter`,
/// `update_text_converter`, `create_xslt`, `update_xslt`, `create_dictionary`, `update_dictionary`, `lost_mapping`
/// and their helpers `_check_converter`, `_checked`, `_described`, `_tag_kept`, `_summary`, `_pending` and
/// `_previewed`).
///
/// The tools that write translation code call these: save_text_converter, save_xslt and save_dictionary, and the
/// builds (build_translation_xslt, build_data_splitter, rebuild_mapping, and draft_cef_mapping's save). Each create
/// goes through the write guard ([WriteGuard#createFilled]), each update checks the document is the tools' own
/// ([WriteGuard#checkManaged]), and each result ends with the build's next step ([BuildPlan]).
public class TranslationDocs {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TranslationDocs.class);

    /// Why an XSLT saved with its mapping keeps none now.
    static final String LOST = "{type} '{name}' was saved by stroom-mcp with the {what} it is generated from, kept "
                               + "in its Documentation tab (hidden, after a note asking that it be left), and it is "
                               + "gone: deleted, or edited so it no longer reads. Until it is back the server treats "
                               + "the XSLT as written by hand (no regenerating, no changes=, no generated Field "
                               + "mapping section), and its version history went with it. Tell the user. To "
                               + "recover: rebuild_mapping uuid='{uuid}' stream_ids=<the sample streams> reads the "
                               + "mapping back from the XSLT and proves it on the sample before saving it.";
    /// What a translation's code that differs from what its mapping generates is warned with.
    static final String HAND_EDITED_WARNING = "The code differs from what the mapping generates: the documentation "
                                              + "will say the XSLT was edited by hand. Prefer changing the mapping "
                                              + "and regenerating.";

    private static final Pattern FRAGMENT_ENTITY = Pattern.compile(
            "<!ENTITY\\s+fragment\\s+SYSTEM\\s+[\"']fragment[\"']");
    private static final Pattern JSON = Pattern.compile("<\\s*json", Pattern.CASE_INSENSITIVE);

    private final WriteGuard guard;
    private final DocumentAccess documentAccess;
    // A provider, as the plan depends on these saves itself (through the build checks' drift).
    private final Provider<BuildPlan> planProvider;
    private final CheckXsltTool checkXslt;
    private final RequiredImports requiredImports;
    private final EventVersions eventVersions;
    private final HandEditGate handEditGate;
    private final XsltHistory history;
    private final LookupAgreements lookups;
    private final Provider<AgentKnowledge> knowledgeProvider;

    /// @param guard             Creates the documents in the build, and checks one is the tools' own.
    /// @param documentAccess    Reads and writes them as the user.
    /// @param planProvider      The build's next step, for each result.
    /// @param checkXslt         Checks an XSLT before it is saved.
    /// @param requiredImports   The shared XSLTs standing instructions require.
    /// @param eventVersions     The event-logging schemas a translation is generated against.
    /// @param handEditGate      Keeps an edit made by hand since the tools saved an XSLT.
    /// @param history           Keeps each XSLT's version history.
    /// @param lookups           Asks the user to agree to the DNS lookups an XSLT calls.
    /// @param knowledgeProvider The bundled ECS reference, for an index plan's convention.
    @Inject
    TranslationDocs(final WriteGuard guard,
                    final DocumentAccess documentAccess,
                    final Provider<BuildPlan> planProvider,
                    final CheckXsltTool checkXslt,
                    final RequiredImports requiredImports,
                    final EventVersions eventVersions,
                    final HandEditGate handEditGate,
                    final XsltHistory history,
                    final LookupAgreements lookups,
                    final Provider<AgentKnowledge> knowledgeProvider) {
        this.guard = guard;
        this.documentAccess = documentAccess;
        this.planProvider = planProvider;
        this.checkXslt = checkXslt;
        this.requiredImports = requiredImports;
        this.eventVersions = eventVersions;
        this.handEditGate = handEditGate;
        this.history = history;
        this.lookups = lookups;
        this.knowledgeProvider = knowledgeProvider;
    }

    // --------------------------------------------------------------------------------
    // Text converters.
    // --------------------------------------------------------------------------------

    /// Creates a text converter in the build folder (see stroom://guide/data-splitter), for templates whose parser
    /// needs one. It is made in the build's Events folder, until a pipeline writing Records runs it
    /// (create_pipeline moves it there).
    ///
    /// @param context       The call's context.
    /// @param build         The build.
    /// @param name          The document's name.
    /// @param converterType `DATA_SPLITTER` or `XML_FRAGMENT`.
    /// @param code          The converter definition.
    /// @return The document (`type`, `uuid`, `name`, `version`), with the build's next step.
    /// @throws ToolRefusal if the code isn't a converter of that type, or the build already has one of the name.
    public Map<String, Object> createTextConverter(final ToolCallContext context,
                                                   final String build,
                                                   final String name,
                                                   final String converterType,
                                                   final String code) {
        checkConverter(converterType, code);
        final TextConverterType type = converterType(converterType);
        final TextConverterDoc saved = guard.createFilled(TextConverterDoc.TYPE, name, build, ref ->
                write(TextConverterDoc.TYPE, read(ref, TextConverterDoc.class).copy()
                        .converterType(type)
                        .data(code)
                        .build()), false, "Events");
        return planProvider.get().withNext(context, build, summary(saved));
    }

    /// Replaces a text converter's code. Only converters the tools created can be changed.
    ///
    /// @param context The call's context.
    /// @param uuid    The converter's UUID.
    /// @param code    The complete new definition.
    /// @param version The version from the last read, or null: the save is refused if it changed since.
    /// @return The document, with the build's next step.
    /// @throws ToolRefusal if it isn't the tools' own, its code isn't a converter of its type, or it changed.
    public Map<String, Object> updateTextConverter(final ToolCallContext context,
                                                   final String uuid,
                                                   final String code,
                                                   final String version) {
        final TextConverterDoc doc = read(new DocRef(TextConverterDoc.TYPE, uuid), TextConverterDoc.class);
        checkConverter(doc.getConverterType() == null
                ? TextConverterType.DATA_SPLITTER.name()
                : doc.getConverterType().name(), code);
        final DocRef ref = new DocRef(TextConverterDoc.TYPE, uuid, doc.getName());
        guard.checkManaged(context.session(), ref);
        checkVersion(doc, version);
        final Map<String, Object> saved = summary(write(TextConverterDoc.TYPE, doc.copy().data(code).build()));
        return planProvider.get().nextFor(context, ref, saved);
    }

    /// A Data Splitter is a `<dataSplitter>` document. JSON needs no text converter at all: the JSONParser element
    /// of a JSON translation template parses it, and a converter holding a `<jsonParser>` element parses nothing.
    ///
    /// @param converterType The converter's type.
    /// @param code          Its definition.
    /// @throws ToolRefusal if the code isn't a converter of that type.
    static void checkConverter(final String converterType, final String code) {
        final String text = NullSafe.string(code);
        if (TextConverterType.XML_FRAGMENT.name().equals(converterType)) {
            if (!FRAGMENT_ENTITY.matcher(text).find() || !text.contains("&fragment;")) {
                throw new ToolRefusal("Not saved: an XML_FRAGMENT converter is the wrapper the XMLFragmentParser "
                                      + "puts round the fragments: a DOCTYPE declaring <!ENTITY fragment SYSTEM "
                                      + "\"fragment\"> and &fragment; inside the root element where the fragments "
                                      + "go. profile_sample gives one (stroom://guide/data-splitter).");
            }
            return;
        }
        if (!TextConverterType.DATA_SPLITTER.name().equals(converterType)) {
            return;
        }
        if (JSON.matcher(text).find()) {
            throw new ToolRefusal("Not saved: a text converter cannot parse JSON. Use a translation template whose "
                                  + "parser is the JSONParser (find_pipeline_templates), whose JSONParser element "
                                  + "parses the raw JSON (JSON lines included) with no text converter; the XSLT then "
                                  + "reads map/string elements in namespace http://www.w3.org/2013/XSL/json "
                                  + "(stroom://guide/json-input).");
        }
        final Element root;
        try {
            root = XmlTrees.parse(text);
        } catch (final XmlTrees.NotWellFormedException e) {
            throw new ToolRefusal("Not saved: the Data Splitter is not well-formed XML (line " + e.getLine() + "): "
                                  + e.getMessage());
        }
        final String local = NullSafe.nonBlankStringElse(root.getLocalName(), root.getNodeName());
        if (!"dataSplitter".equals(local)) {
            throw new ToolRefusal("Not saved: a DATA_SPLITTER converter's root element is <dataSplitter "
                                  + "xmlns=\"data-splitter:3\">, not <" + local + "> (stroom://guide/data-splitter)");
        }
    }

    // --------------------------------------------------------------------------------
    // XSLTs.
    // --------------------------------------------------------------------------------

    /// Creates an XSLT in the build folder. It is checked with check_xslt first and not saved if that fails. The
    /// mapping (or plan) it was generated from is kept with it, and the pipeline's documentation is generated from
    /// it. One calling Stroom's DNS lookup functions is saved once the user agrees ([LookupAgreements]).
    ///
    /// @param call    The call, whose id tells its forms apart.
    /// @param context The call's context.
    /// @param build   The build.
    /// @param name    The document's name.
    /// @param code    The complete XSLT.
    /// @param source  The mapping or plan it was generated from, or [Source#NONE] for one written by hand.
    /// @param change  What this change is, for the version history, or null (`Created`).
    /// @return The document, with `matches_mapping`, `warning` or `ecs_check` where they apply, and the build's
    /// next step.
    /// @throws ToolRefusal if the checks fail, a required import is missing, the build has an XSLT of the name, or
    ///                     the user didn't agree to its DNS lookups.
    public Map<String, Object> createXslt(final ToolCall call,
                                          final ToolCallContext context,
                                          final String build,
                                          final String name,
                                          final String code,
                                          final Source source,
                                          final String change) {
        checked(context, code, build, null);
        // A plan says what an XSLT is for; a discovery XSLT reads a source's records, as one writing Records does.
        final String role = source.indexPlan() != null
                ? "Indexing"
                : source.cefPlan() != null
                        ? "CEF"
                        : RequiredImports.xsltRole(code);
        requiredImports.refuseWithout(context.session(), code, build, role);
        lookups.require(call, context, build + "/" + name, "Save XSLT '" + name + "'", "XSLT not saved",
                XsltChecks.unescaped(code).text(), null);
        final Map<String, Object> extra = new LinkedHashMap<>();
        final XsltDoc saved = guard.createFilled(XsltDoc.TYPE, name, build, ref -> {
            final XsltDoc made = read(ref, XsltDoc.class);
            String description = described(made.getDescription(), code, source, extra);
            description = history.pending(context.session(), description, null,
                    NullSafe.nonBlankStringElse(change, "Created"), code);
            description = history.previewed(description, code);
            return write(XsltDoc.TYPE, made.copy().data(code).description(description).build());
        }, false, role);
        tagKept(saved, source.keeps());
        final Map<String, Object> result = summary(saved);
        result.putAll(extra);
        return planProvider.get().withNext(context, build, result);
    }

    /// Replaces an XSLT's code, after check_xslt passes. Only XSLTs the tools created (including working copies of
    /// production XSLTs) can be changed. Code that undoes an edit made by hand since the tools saved the XSLT is
    /// refused, unless the user said to drop it; where the change is to a field the edit changed too, the user is
    /// asked whether to keep their edit or overwrite it. Code calling one of Stroom's DNS lookup functions the XSLT
    /// doesn't call already is saved once the user agrees ([LookupAgreements]).
    ///
    /// @param call            The call, whose id tells its forms apart.
    /// @param context         The call's context.
    /// @param uuid            The XSLT's UUID.
    /// @param code            The complete new XSLT.
    /// @param version         The version from the last read, or null: the save is refused if it changed since.
    /// @param source          The mapping or plan the new code was generated from, or [Source#NONE].
    /// @param change          What this change is, for the version history, or null (`Changed`).
    /// @param discardHandEdit True when the user said to drop their hand edit.
    /// @param choices         The user's answers about fields edited by hand (`hand_edit_choices`), or null.
    /// @return The document, with the build's next step; or the question about a hand edit (a result with
    /// `status` and no `uuid`), for the caller to return: nothing is saved then.
    /// @throws ToolRefusal if the checks fail, it isn't the tools' own, a required import is missing, the code
    ///                     undoes a hand edit, it changed since it was read, or the user didn't agree to its DNS
    ///                     lookups.
    public Map<String, Object> updateXslt(final ToolCall call,
                                          final ToolCallContext context,
                                          final String uuid,
                                          final String code,
                                          final String version,
                                          final Source source,
                                          final String change,
                                          final boolean discardHandEdit,
                                          final Map<String, String> choices) {
        final XsltDoc doc = readXslt(uuid);
        checked(context, code, null, doc.getData());
        final DocRef ref = new DocRef(XsltDoc.TYPE, uuid, doc.getName());
        guard.checkManaged(context.session(), ref);
        final Optional<String> build = guard.buildOf(ref);
        if (build.isPresent()) {
            requiredImports.refuseWithout(context.session(), code, build.get(), source.indexPlan() != null
                    ? "Indexing"
                    : source.cefPlan() != null
                            ? "CEF"
                            : null);
        }
        if (!discardHandEdit) {
            final Optional<Map<String, Object>> gate = handEditGate.check(call, context, doc, code, source.kind(),
                    source.handEditPayload(), choices);
            if (gate.isPresent()) {
                return gate.get();
            }
        }
        checkVersion(doc, version);
        lookups.require(call, context, uuid, "Save XSLT '" + doc.getName() + "'", "XSLT not saved",
                XsltChecks.unescaped(code).text(), doc.getData());
        // The history lives in the description, not the code: one an earlier version kept in the code moves there.
        String description = XsltVersionHistory.adopt(doc.getDescription(), doc.getData());
        final String data = XsltVersionHistory.strip(code);
        final Map<String, Object> extra = new LinkedHashMap<>();
        description = described(description, code, source, extra);
        description = history.pending(context.session(), description, new XsltVersionHistory.Previous(doc.getData(),
                doc.getUpdateUser(), doc.getUpdateTimeMs()), NullSafe.nonBlankStringElse(change, "Changed"), data);
        description = history.previewed(description, data);
        final XsltDoc saved = write(XsltDoc.TYPE, doc.copy().data(data).description(description).build());
        tagKept(saved, source.keeps());
        final Map<String, Object> result = summary(saved);
        result.putAll(extra);
        return planProvider.get().nextFor(context, ref, result);
    }

    /// @param uuid An XSLT's UUID.
    /// @return It, as the user reads it.
    /// @throws ToolRefusal if it doesn't exist, or the user may not view it.
    public XsltDoc readXslt(final String uuid) {
        return read(new DocRef(XsltDoc.TYPE, uuid), XsltDoc.class);
    }

    /// Why an XSLT has no kept mapping, when it should have one: tagged as saved with one, and none reads now.
    ///
    /// @param ref         The XSLT.
    /// @param description Its description.
    /// @return Why, or empty when it keeps one, or never did.
    public Optional<String> lostMapping(final DocRef ref, final String description) {
        if (KeptMapping.readMapping(description).isPresent()) {
            return Optional.empty();
        }
        final Set<String> tags;
        try {
            tags = guard.tags(ref);
        } catch (final RuntimeException e) {
            return Optional.empty();
        }
        if (!tags.contains(WriteGuard.KEPT_MAPPING)) {
            return Optional.empty();
        }
        return Optional.of(LOST.replace("{type}", NullSafe.nonBlankStringElse(ref.getType(), XsltDoc.TYPE))
                .replace("{name}", String.valueOf(ref.getName()))
                .replace("{uuid}", String.valueOf(ref.getUuid()))
                .replace("{what}", "mapping (or plan)"));
    }

    /// check_xslt, or a refusal. With the code being replaced, its refused calls and instructions stay allowed: a
    /// working copy keeps what its production XSLT already did, written by a person. Stroom's DNS lookup functions
    /// are not refused here: the save asks the user to agree to them ([LookupAgreements]).
    ///
    /// @param context  The call's context.
    /// @param xslt     The XSLT.
    /// @param build    Its build, or null: a source's own XML in no namespace is then read as it is (its
    ///                 `<records><record>` are not a Data Splitter's records:2).
    /// @param previous The code being replaced, or null.
    /// @throws ToolRefusal saying what check_xslt found.
    void checked(final ToolCallContext context, final String xslt, final String build, final String previous) {
        String inputNamespace = null;
        if (NullSafe.isNonBlankString(build)) {
            try {
                final Optional<Map<String, Object>> sample = planProvider.get().sampleFormat(build);
                if (sample.isPresent() && "xml".equals(sample.get().get("format"))) {
                    inputNamespace = NullSafe.string(NullSafe.get(sample.get().get("namespace"), String::valueOf));
                }
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("No sample format for build {}: {}", build, e.getMessage()));
            }
        }
        List<String> errors = errors(checkXslt.check(xslt, null, inputNamespace));
        if (NullSafe.isNonEmptyString(previous)) {
            final Set<String> allowed = XsltSafety.keptBy(XsltChecks.unescaped(xslt).text(), previous);
            errors = errors.stream().filter(error -> !allowed.contains(error)).toList();
        }
        if (!errors.isEmpty()) {
            throw new ToolRefusal("XSLT not saved: " + String.join("; ", errors));
        }
    }

    // --------------------------------------------------------------------------------
    // Dictionaries.
    // --------------------------------------------------------------------------------

    /// Creates a Dictionary doc in the build: a small static table a translation reads at run time with
    /// stroom:dictionary().
    ///
    /// @param build       The build.
    /// @param name        Its name, as the mapping's `dictionary` / `in_dictionary` name it.
    /// @param text        One entry per line.
    /// @param description What the entries are and where they came from.
    /// @return The document, with how many entries it has.
    /// @throws ToolRefusal if the build already has a Dictionary of the name.
    public Map<String, Object> createDictionary(final String build,
                                                final String name,
                                                final String text,
                                                final String description) {
        final DictionaryDoc saved = guard.createFilled(DictionaryDoc.TYPE, name, build, ref ->
                write(DictionaryDoc.TYPE, read(ref, DictionaryDoc.class).copy()
                        .data(text)
                        .description(NullSafe.string(description))
                        .build()), false, null);
        return withEntries(summary(saved), text);
    }

    /// Replaces a dictionary's entries. Only dictionaries the tools created can be changed.
    ///
    /// @param context The call's context.
    /// @param uuid    The Dictionary's UUID.
    /// @param text    The complete new content, one entry per line.
    /// @param version The version from the last read, or null.
    /// @return The document, with how many entries it has.
    /// @throws ToolRefusal if it isn't the tools' own, or it changed since it was read.
    public Map<String, Object> updateDictionary(final ToolCallContext context,
                                                final String uuid,
                                                final String text,
                                                final String version) {
        final DictionaryDoc doc = read(new DocRef(DictionaryDoc.TYPE, uuid), DictionaryDoc.class);
        guard.checkManaged(context.session(), new DocRef(DictionaryDoc.TYPE, uuid, doc.getName()));
        checkVersion(doc, version);
        return withEntries(summary(write(DictionaryDoc.TYPE, doc.copy().data(text).build())), text);
    }

    // --------------------------------------------------------------------------------
    // Helpers.
    // --------------------------------------------------------------------------------

    /// @param doc A document.
    /// @return It as the tools' results name it: `type`, `uuid`, `name` and `version`.
    static Map<String, Object> summary(final AbstractDoc doc) {
        final Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("type", doc.getType());
        summary.put("uuid", doc.getUuid());
        summary.put("name", doc.getName());
        summary.put("version", doc.getVersion());
        return summary;
    }

    // The description carrying the mapping or plan the code came from; whether the code is what the mapping
    // generates (a hand-edited XSLT is kept, but reported) goes in extra.
    private String described(final String description,
                             final String code,
                             final Source source,
                             final Map<String, Object> extra) {
        if (source.mapping() != null) {
            // The version the code was generated for.
            final String version = eventVersions.translationVersion(null, code);
            final Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("schema_version", version);
            payload.put("mapping", source.mapping().toJson(DumpMode.COMPACT));
            final String withMapping = KeptMapping.withMapping(description, KeptMapping.TRANSLATION, payload);
            try {
                final String regenerated = XsltGenerator.generate(source.mapping(),
                        eventVersions.eventSchema(version), version).xslt();
                final boolean matches = XsltCode.normalise(NullSafe.string(regenerated))
                        .equals(XsltCode.normalise(code));
                extra.put("matches_mapping", matches);
                if (!matches) {
                    extra.put("warning", HAND_EDITED_WARNING);
                }
            } catch (final RuntimeException e) {
                // The schema may be unavailable here; the comparison is advice, not a gate.
                LOGGER.debug(() -> LogUtil.message("Not compared with what the mapping generates: {}", e.getMessage()));
            }
            return withMapping;
        }
        if (source.indexPlan() != null) {
            final List<String> problems = conventionProblems(source.indexPlan());
            if (!problems.isEmpty()) {
                // Saved all the same: a field of the user's own may be meant. Said so they decide.
                extra.put("ecs_check", problems);
            }
            return KeptMapping.withMapping(description, "index", source.indexPlan().toJson());
        }
        if (source.cefPlan() != null) {
            return KeptMapping.withMapping(description, "cef", source.cefPlan().toJson(DumpMode.COMPACT));
        }
        return NullSafe.string(description);
    }

    private List<String> conventionProblems(final FieldPlan indexPlan) {
        try {
            final Optional<EcsSchema> ecs = EcsSchema.from(knowledgeProvider.get());
            return ecs.map(indexPlan::conventionProblems).orElse(List.of());
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("The plan's convention not checked: {}", e.getMessage()));
            return List.of();
        }
    }

    // Tags an XSLT saved with its mapping, so a mapping later deleted from its Documentation tab is noticed.
    private void tagKept(final XsltDoc saved, final boolean kept) {
        if (!kept) {
            return;
        }
        try {
            guard.tag(List.of(new DocRef(XsltDoc.TYPE, saved.getUuid(), saved.getName())),
                    Set.of(WriteGuard.KEPT_MAPPING));
        } catch (final RuntimeException e) {
            // A tag is a safeguard: the save stands without it.
            LOGGER.debug(() -> LogUtil.message("Couldn't tag XSLT {}: {}", saved.getUuid(), e.getMessage()));
        }
    }

    private <T> T read(final DocRef ref, final Class<T> type) {
        return documentAccess.read(ref, type)
                .orElseThrow(() -> new ToolRefusal(ref.getType() + " " + ref.getUuid() + " can't be read: it "
                                                   + "doesn't exist, or you don't have permission to view it"));
    }

    @SuppressWarnings("unchecked")
    private <T extends AbstractDoc> T write(final String type, final T doc) {
        final DocumentActionHandler<T> handler = documentAccess.handler(type)
                .orElseThrow(() -> new ToolRefusal("Stroom has no " + type + " store"));
        return handler.writeDocument(doc);
    }

    // With the version from the last read, the save is refused if someone else saved the document since.
    private static void checkVersion(final AbstractDoc current, final String version) {
        if (version != null && !Objects.equals(current.getVersion(), version)) {
            throw new ToolRefusal(current.getType() + " '" + current.getName() + "' changed since it was read "
                                  + "(version " + current.getVersion() + "); read it again and reapply the change");
        }
    }

    private static TextConverterType converterType(final String converterType) {
        try {
            return TextConverterType.valueOf(NullSafe.string(converterType).toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            throw new ToolRefusal("converter_type is DATA_SPLITTER or XML_FRAGMENT, not '" + converterType + "'");
        }
    }

    private static Map<String, Object> withEntries(final Map<String, Object> summary, final String text) {
        summary.put("entries", NullSafe.string(text).lines().filter(line -> !line.isBlank()).count());
        return summary;
    }

    @SuppressWarnings("unchecked")
    private static List<String> errors(final Map<String, Object> result) {
        return result.get("errors") instanceof final List<?> list
                ? (List<String>) list
                : List.of();
    }


    // --------------------------------------------------------------------------------


    /// What an XSLT's code was generated from, kept with it in its description: a translation mapping, an index
    /// plan or a CEF plan, or nothing (written by hand). At most one.
    ///
    /// @param mapping   The translation mapping, or null.
    /// @param indexPlan The index plan, or null.
    /// @param cefPlan   The CEF plan, or null.
    public record Source(TranslationMapping mapping, FieldPlan indexPlan, CefPlan cefPlan) {

        /// Written by hand.
        public static final Source NONE = new Source(null, null, null);

        /// @param mapping A translation mapping.
        /// @return The code generated from it.
        public static Source of(final TranslationMapping mapping) {
            return new Source(mapping, null, null);
        }

        /// @param indexPlan An index plan, or null.
        /// @return The code generated from it.
        public static Source of(final FieldPlan indexPlan) {
            return new Source(null, indexPlan, null);
        }

        /// @param cefPlan A CEF plan.
        /// @return The code generated from it.
        public static Source of(final CefPlan cefPlan) {
            return new Source(null, null, cefPlan);
        }

        /// @return True if a mapping or plan is kept with the code.
        boolean keeps() {
            return mapping != null || indexPlan != null || cefPlan != null;
        }

        /// @return The kind kept: `translation`, `index` or `cef`; null for code written by hand.
        String kind() {
            if (mapping != null) {
                return KeptMapping.TRANSLATION;
            }
            if (indexPlan != null) {
                return "index";
            }
            return cefPlan != null
                    ? "cef"
                    : null;
        }

        /// @return What the hand-edit check compares the kept one with: `{mapping}` for a translation, the plan
        /// for the others; null for code written by hand.
        Map<String, Object> handEditPayload() {
            if (mapping != null) {
                final Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("mapping", mapping.toJson(DumpMode.COMPACT));
                return payload;
            }
            if (indexPlan != null) {
                return indexPlan.toJson();
            }
            return cefPlan != null
                    ? cefPlan.toJson(DumpMode.COMPACT)
                    : null;
        }
    }
}
