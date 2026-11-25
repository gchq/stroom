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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.catalogue.Catalogue;
import stroom.ai.impl.tools.catalogue.CatalogueFromMapping;
import stroom.ai.impl.tools.catalogue.EventType;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.documents.KeptMapping;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.RebuiltMapping;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XsltReader;
import stroom.ai.impl.tools.processing.MergedPipeline;
import stroom.ai.impl.tools.processing.TranslationInputText;
import stroom.ai.impl.tools.processing.XsltStages;
import stroom.ai.impl.tools.processing.XsltStages.Stage;
import stroom.ai.impl.tools.processing.XsltStages.StagedXslt;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.docstore.api.DocumentActionHandler;
import stroom.documentation.shared.DocumentationDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.xmlschema.shared.XmlSchemaDoc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;

/// Describing a hand-written translation in place (decided with the user 2026-11-25): an existing pipeline's
/// translation read into the tools' knowledge without a new version. Its translation XSLT ([XsltStages]: never a
/// pre-processor before it) is read back into a mapping as rebuild_mapping reads one ([XsltReader]), and that into
/// a catalogue of event types; then, without asking (only the tools' hidden blocks change):
///
/// - the mapping is kept in the XSLT's description, with the hidden link to its source notes
///   ([KeptMapping#SOURCE_NOTES]): its code, and the user's own text, are left as they are (needs Edit on it);
/// - the catalogue is kept in a source notes doc beside the pipeline (made there, which needs Create in its folder,
///   or the one already there), the build's notes a working copy of it, written back once confirmed.
///
/// Where the user may not, who can is said and the induction is kept in the build only (its notes, and the mapping
/// in its record), to be written back when it is confirmed by someone who may. The survey covers every feed of
/// every pipeline using the XSLT ([XsltUsers]), stated first for the user to narrow; the catalogue records the
/// feeds it was checked against. Where a pre-processor feeds the translation, the build's records are read at the
/// translation's input ([TranslationInputs]).
class InPlaceInduction {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(InPlaceInduction.class);

    /// The build record's choice: the existing pipeline described in place; also the key of what was described.
    static final String IN_PLACE = "in_place";
    /// The described pipeline's translation element.
    static final String ELEMENT = "translation_element";
    /// True where a pre-processor feeds the translation.
    static final String PRE_PROCESSED = "pre_processed";
    /// The feeds surveyed, as the user chose them.
    static final String FEEDS = "feeds";
    /// The kept mapping's key saying it was read from a hand-written XSLT, not generated.
    static final String INDUCTED = "inducted";
    // The record's keys of what was written in place.
    private static final String XSLT = "xslt";
    private static final String NOTES = "notes";
    private static final String MAPPING = "mapping";
    private static final String WRITTEN = "written";
    private static final String NOT_WRITTEN = "not_written";

    private final PipelineShapes pipelineShapes;
    private final XsltStages xsltStages;
    private final DocumentAccess documentAccess;
    private final DocumentWrites documentWrites;
    private final WriteGuard guard;
    private final CatalogueNotes catalogueNotes;
    private final XsltUsers xsltUsers;
    private final EventVersions eventVersions;
    private final Provider<DocFinder> docFinder;
    private final Provider<BuildRecords> buildRecords;
    private final LongSupplier clock;

    @Inject
    InPlaceInduction(final PipelineShapes pipelineShapes,
                     final XsltStages xsltStages,
                     final DocumentAccess documentAccess,
                     final DocumentWrites documentWrites,
                     final WriteGuard guard,
                     final CatalogueNotes catalogueNotes,
                     final XsltUsers xsltUsers,
                     final EventVersions eventVersions,
                     final Provider<DocFinder> docFinder,
                     final Provider<BuildRecords> buildRecords) {
        this(pipelineShapes, xsltStages, documentAccess, documentWrites, guard, catalogueNotes, xsltUsers,
                eventVersions, docFinder, buildRecords, System::currentTimeMillis);
    }

    /// As the injected constructor, with the clock given, for tests.
    ///
    /// @param pipelineShapes Reads a pipeline as Stroom runs it.
    /// @param xsltStages     Tells the translation XSLT from a pipeline's others.
    /// @param documentAccess Reads and writes documents, and checks permissions, as the user.
    /// @param documentWrites Reads and writes Documentation docs.
    /// @param guard          The build's documents.
    /// @param catalogueNotes The build's catalogue, and the notes beside a pipeline.
    /// @param xsltUsers      Every pipeline running the XSLT, and their feeds.
    /// @param eventVersions  The schema the XSLT writes.
    /// @param docFinder      Finds the XSLTs it imports.
    /// @param buildRecords   The build records: how the build started.
    /// @param clock          The time now, in milliseconds.
    InPlaceInduction(final PipelineShapes pipelineShapes,
                     final XsltStages xsltStages,
                     final DocumentAccess documentAccess,
                     final DocumentWrites documentWrites,
                     final WriteGuard guard,
                     final CatalogueNotes catalogueNotes,
                     final XsltUsers xsltUsers,
                     final EventVersions eventVersions,
                     final Provider<DocFinder> docFinder,
                     final Provider<BuildRecords> buildRecords,
                     final LongSupplier clock) {
        this.pipelineShapes = pipelineShapes;
        this.xsltStages = xsltStages;
        this.documentAccess = documentAccess;
        this.documentWrites = documentWrites;
        this.guard = guard;
        this.catalogueNotes = catalogueNotes;
        this.xsltUsers = xsltUsers;
        this.eventVersions = eventVersions;
        this.docFinder = docFinder;
        this.buildRecords = buildRecords;
        this.clock = clock;
    }

    /// @param startFrom A build's start ([BuildRecords#startFrom]), or null.
    /// @return True if the build describes an existing pipeline in place.
    static boolean inPlace(final Map<String, Object> startFrom) {
        return startFrom != null && IN_PLACE.equals(startFrom.get("choice"));
    }

    /// What describing a pipeline in place would cover: its translation XSLT, whether a pre-processor feeds it, and
    /// every pipeline running that XSLT with their feeds.
    ///
    /// @param pipelineUuid The existing pipeline.
    /// @return Its scope; empty where no translation can be told in it (it is then only copied or drafted afresh).
    Optional<Scope> scope(final String pipelineUuid) {
        try {
            final MergedPipeline merged = pipelineShapes.merged(pipelineUuid);
            final List<StagedXslt> staged = xsltStages.classify(merged);
            final Optional<StagedXslt> translation = XsltStages.first(staged, Stage.TRANSLATION)
                    .filter(xslt -> xslt.xslt() != null);
            if (translation.isEmpty()) {
                return Optional.empty();
            }
            final boolean preProcessed = staged.indexOf(translation.get()) > 0
                                         && staged.subList(0, staged.indexOf(translation.get())).stream()
                                                 .anyMatch(xslt -> xslt.stage() == Stage.PRE_PROCESS);
            final DocRef pipeline = new DocRef(PipelineDoc.TYPE, pipelineUuid, NullSafe.get(merged.doc(),
                    PipelineDoc::getName));
            return Optional.of(new Scope(pipeline, translation.get(), preProcessed,
                    xsltUsers.of(translation.get().xslt().getUuid())));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Pipeline {} can't be described in place: {}", pipelineUuid,
                    e.getMessage()), e);
            return Optional.empty();
        }
    }

    /// Describes the pipeline in place: its translation's mapping and catalogue read back from its XSLT, kept with
    /// the XSLT and in a source notes doc beside the pipeline, without asking, where the user may; in the build
    /// only where not.
    ///
    /// @param context The call's context.
    /// @param build   The build.
    /// @param source  The source's name, for a new notes doc's.
    /// @param scope   What it covers ([#scope]).
    /// @param feeds   The feeds to survey, as the user chose them.
    /// @return What was read and written: `status` (`described_in_place`, or `not_read` where the XSLT can't be
    /// read back), `xslt`, `pipelines`, `feeds`, `event_types`, `written` and `not_written` (with who can).
    Map<String, Object> describe(final ToolCallContext context,
                                 final String build,
                                 final String source,
                                 final Scope scope,
                                 final List<String> feeds) {
        final DocRef xsltRef = scope.translation().xslt();
        final XsltDoc xslt = documentAccess.read(xsltRef, XsltDoc.class)
                .orElseThrow(() -> new ToolRefusal("XSLT '" + xsltRef.getName() + "' can't be read: it can't be "
                                                   + "described in place"));
        final String code = NullSafe.string(xslt.getData());
        final String version = eventVersions.translationVersion(null, code);
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schema_version", version);
        eventVersions.forkWrittenBy(code).map(XmlSchemaDoc::getUuid)
                .ifPresent(uuid -> payload.put(EventVersions.KEPT_SCHEMA_UUID, uuid));
        final EventSchema schema = eventVersions.keptSchema(context, build, payload, version);
        final RebuiltMapping rebuilt = XsltReader.rebuild(code, null, null, RebuildMappingTool.imported(
                docFinder.get(), documentAccess, code), schema);
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put(XSLT, ref(xsltRef, xslt.getName()));
        out.put("pipelines", scope.users().users().stream().map(user -> user.pipeline().getName()).toList());
        out.put(FEEDS, feeds);
        if (rebuilt.mapping() == null || !rebuilt.problems().isEmpty()) {
            out.put("status", "not_read");
            out.put("rebuilt", rebuilt.summary());
            out.put("hint", "The XSLT couldn't be read back into a mapping (rebuilt says what): nothing was written. "
                            + "Copy it as a new version, or draft a new translation, instead.");
            return out;
        }
        payload.put(MAPPING, rebuilt.mapping().toJson(DumpMode.COMPACT));
        final Map<String, Object> inducted = new LinkedHashMap<>();
        inducted.put("at", StreamProcessing.iso(clock.getAsLong()));
        inducted.put("build", build);
        inducted.put("note", "read from the hand-written XSLT, which stays as it is: the XSLT is the authority");
        payload.put(INDUCTED, inducted);

        // The catalogue: beside the pipeline, the build's notes a working copy of it.
        final DocRef pipeline = scope.pipeline();
        Optional<DocRef> original = besidePipeline(pipeline);
        original.ifPresent(found -> catalogueNotes.carryInto(build, pipeline));
        final Catalogue before = catalogueNotes.catalogue(build).orElse(null);
        final Catalogue catalogue = readBack(CatalogueFromMapping.read(rebuilt.mapping(), before), before)
                .withCheckedFeeds(feeds);
        catalogueNotes.write(context.session(), build, source, catalogue, reading(rebuilt.mapping(),
                scope.preProcessed()));
        final List<String> notWritten = new ArrayList<>();
        final Optional<DocRef> folder = documentAccess.parent(pipeline);
        if (original.isEmpty()) {
            final boolean canCreate = folder.map(found -> documentAccess.canCreate(found,
                    DocumentationDoc.TYPE)).orElse(false);
            if (canCreate) {
                original = madeBeside(build, pipeline, folder.get());
            } else {
                notWritten.add("Create (Documentation) in folder '" + folder.map(documentAccess::folderPath)
                        .orElse("?") + "', for the source notes beside the pipeline");
            }
        } else if (documentAccess.canEdit(original.get())) {
            writtenBack(build, original.get());
        } else {
            notWritten.add("Edit on the source notes '" + original.get().getName() + "'");
        }
        final boolean xsltWritten = documentAccess.canEdit(xsltRef) && writeXslt(xslt, payload,
                original.orElse(null));
        if (!xsltWritten) {
            notWritten.addFirst("Edit on XSLT '" + documentAccess.path(xsltRef) + "', for its mapping");
        }
        final Map<String, Object> written = new LinkedHashMap<>();
        written.put("xslt_description", xsltWritten
                ? "its mapping and the link to its source notes, in hidden blocks (its code and the text above "
                  + "them kept)"
                : null);
        written.put(NOTES, original.map(notes -> ref(notes, notes.getName())).orElse(null));
        remember(build, scope, feeds, original.orElse(null), xsltWritten, xsltWritten
                ? null
                : payload);
        out.put("status", "described_in_place");
        out.put("rebuilt", rebuilt.summary());
        out.put("event_types", catalogue.eventTypes().stream().map(EventType::typeId).toList());
        out.put(WRITTEN, written);
        if (!notWritten.isEmpty()) {
            out.put(NOT_WRITTEN, whoCan(notWritten));
        }
        return out;
    }

    /// Once the catalogue is confirmed, it is written back to the notes beside the pipeline (with the feeds it was
    /// checked against), and the XSLT's mapping written where it wasn't before (the user may now); only where the
    /// build describes a pipeline in place.
    ///
    /// @param context The call's context.
    /// @param build   The build.
    /// @return What was written back, said; empty for a build that doesn't describe one in place.
    Optional<Map<String, Object>> confirmed(final ToolCallContext context, final String build) {
        final Map<String, Object> startFrom = buildRecords.get().startFrom(build).orElse(null);
        if (!inPlace(startFrom)) {
            return Optional.empty();
        }
        final Map<String, Object> described = new LinkedHashMap<>(JsonValues.mapOrEmpty(startFrom.get(IN_PLACE)));
        final List<String> feeds = JsonValues.strings(described.get(FEEDS));
        catalogueNotes.catalogue(build).ifPresent(catalogue -> {
            if (!catalogue.checkedFeeds().equals(feeds) && !feeds.isEmpty()) {
                catalogueNotes.write(context.session(), build, build, catalogue.withCheckedFeeds(feeds), null);
            }
        });
        final Map<String, Object> out = new LinkedHashMap<>();
        final List<String> notWritten = new ArrayList<>();
        final String notesUuid = JsonValues.text(JsonValues.mapOrEmpty(described.get(NOTES)).get("uuid"));
        final Optional<DocRef> original = notesUuid.isEmpty()
                ? Optional.empty()
                : documentAccess.findDocRef(notesUuid);
        if (original.isPresent() && documentAccess.canEdit(original.get())) {
            writtenBack(build, original.get());
            out.put(NOTES, "the confirmed catalogue written back to '" + original.get().getName() + "'");
        } else {
            notWritten.add(original.map(notes -> "Edit on the source notes '" + notes.getName() + "'")
                    .orElse("Create (Documentation) beside the pipeline, for its source notes"));
        }
        final Map<String, Object> keptMapping = JsonValues.mapOrEmpty(described.get(MAPPING));
        final String xsltUuid = JsonValues.text(JsonValues.mapOrEmpty(described.get(XSLT)).get("uuid"));
        if (!keptMapping.isEmpty() && !xsltUuid.isEmpty()) {
            final DocRef xsltRef = new DocRef(XsltDoc.TYPE, xsltUuid);
            final Optional<XsltDoc> xslt = documentAccess.read(xsltRef, XsltDoc.class);
            if (xslt.isPresent() && documentAccess.canEdit(xsltRef)
                && writeXslt(xslt.get(), keptMapping, original.orElse(null))) {
                described.remove(MAPPING);
                described.put(WRITTEN, true);
                final Map<String, Object> recorded = new LinkedHashMap<>(startFrom);
                recorded.put(IN_PLACE, described);
                buildRecords.get().rememberStartFrom(build, recorded);
                out.put(XSLT, "its mapping written in the XSLT's description now");
            } else {
                notWritten.add("Edit on XSLT '" + xsltRef.getUuid() + "', for its mapping");
            }
        }
        if (!notWritten.isEmpty()) {
            out.put(NOT_WRITTEN, whoCan(notWritten));
        }
        return Optional.of(out);
    }

    // What the user lacks, and who can write it in place instead: the induction is kept in the build until then.
    private static String whoCan(final List<String> lacking) {
        return "Not written in place: it needs " + String.join(", and ", lacking) + ". The document's owner, or "
               + "anyone with those permissions, can describe it in place (start a build from it as you did); until "
               + "then the description is kept in this build only (its source notes, and the mapping in its "
               + "record), and written in place when it is confirmed by someone who may.";
    }

    // The notes beside the pipeline, outside any build.
    private Optional<DocRef> besidePipeline(final DocRef pipeline) {
        return catalogueNotes.notesBeside(pipeline).filter(notes -> guard.buildOf(notes).isEmpty());
    }

    // The build's notes moved beside the pipeline (Stroom checks the user may), then carried back into the build as
    // its working copy: the notes beside the pipeline, made.
    private Optional<DocRef> madeBeside(final String build, final DocRef pipeline, final DocRef folder) {
        final Optional<DocRef> notes = catalogueNotes.notesDocRef(build);
        if (notes.isEmpty()) {
            return Optional.empty();
        }
        guard.moveTo(notes.get(), new WriteGuard.Folder(folder, documentAccess.folderPath(folder), false));
        catalogueNotes.carryInto(build, pipeline);
        return Optional.of(notes.get());
    }

    // The build's notes (its working copy) written over the notes beside the pipeline.
    private void writtenBack(final String build, final DocRef original) {
        catalogueNotes.notesDocRef(build).ifPresent(copy -> documentWrites.copyFields(copy, original,
                BuildDocumentation.DOC_FIELDS));
    }

    // The XSLT's description with the mapping and the link to the notes in hidden blocks, its code and the user's
    // own text kept; false where Stroom has no XSLT store.
    @SuppressWarnings("unchecked")
    private boolean writeXslt(final XsltDoc xslt, final Map<String, Object> payload, final DocRef notes) {
        String description = KeptMapping.withMapping(xslt.getDescription(), KeptMapping.TRANSLATION, payload);
        if (notes != null) {
            description = KeptMapping.withSourceNotes(description, notes);
        }
        if (Objects.equals(description, xslt.getDescription())) {
            return true;
        }
        final Optional<DocumentActionHandler> handler = documentAccess.handler(XsltDoc.TYPE);
        final String described = description;
        handler.ifPresent(store -> store.writeDocument(xslt.copy().description(described).build()));
        return handler.isPresent();
    }

    // What was described, kept in the build record's start: the translation, its element, whether a pre-processor
    // feeds it, the feeds, the notes beside the pipeline, and the mapping where it couldn't be written.
    private void remember(final String build,
                          final Scope scope,
                          final List<String> feeds,
                          final DocRef notes,
                          final boolean xsltWritten,
                          final Map<String, Object> unwritten) {
        final Map<String, Object> startFrom = new LinkedHashMap<>(buildRecords.get().startFrom(build)
                .orElse(Map.of()));
        final Map<String, Object> described = new LinkedHashMap<>();
        described.put(XSLT, ref(scope.translation().xslt(), scope.translation().xslt().getName()));
        described.put(ELEMENT, scope.translation().element());
        described.put(PRE_PROCESSED, scope.preProcessed());
        described.put(FEEDS, feeds);
        described.put(NOTES, notes == null
                ? null
                : ref(notes, notes.getName()));
        described.put(WRITTEN, xsltWritten);
        if (unwritten != null) {
            described.put(MAPPING, unwritten);
        }
        startFrom.put(IN_PLACE, described);
        buildRecords.get().rememberStartFrom(build, startFrom);
    }

    // The catalogue read back proposed for the user to confirm, its TypeIds as the XSLT writes them; those the
    // catalogue had confirmed already kept so.
    private static Catalogue readBack(final CatalogueFromMapping.FromMapping read, final Catalogue before) {
        final Set<String> known = new HashSet<>();
        if (before != null) {
            before.eventTypes().stream().filter(EventType::confirmed).map(EventType::typeId).forEach(known::add);
        }
        return read.catalogue().withEventTypes(read.catalogue().eventTypes().stream()
                .map(type -> known.contains(type.typeId())
                        ? type
                        : type.readBack())
                .toList());
    }

    /// How the catalogue reads its records, from the mapping read back: its reading keys, and where a
    /// pre-processor feeds the translation, read as [TranslationInputText] gives its input (XML of JSON and records
    /// as JSON, other XML as XML).
    ///
    /// @param mapping      The mapping read back.
    /// @param preProcessed True where a pre-processor feeds the translation.
    /// @return The reading.
    static Map<String, Object> reading(final TranslationMapping mapping, final boolean preProcessed) {
        final Map<String, Object> json = mapping.toJson(DumpMode.COMPACT);
        final Map<String, Object> reading = new LinkedHashMap<>();
        CatalogueReading.READING_KEYS.forEach(key -> {
            if (json.get(key) != null) {
                reading.put(key, json.get(key));
            }
        });
        if (preProcessed && !"xml".equals(mapping.input()) && !"xml_fragments".equals(mapping.input())) {
            // The pre-processor's output read as JSON ([TranslationInputText]): its records by their names.
            reading.put("input", "json");
            reading.remove("record");
            reading.remove("xml_namespace");
            reading.remove("root");
        }
        return reading;
    }

    private static Map<String, Object> ref(final DocRef ref, final String name) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", ref.getType());
        out.put("uuid", ref.getUuid());
        out.put("name", NullSafe.nonBlankStringElse(name, ref.getName()));
        return out;
    }


    // --------------------------------------------------------------------------------


    /// What describing a pipeline in place covers.
    ///
    /// @param pipeline     The existing pipeline.
    /// @param translation  Its translation XSLT.
    /// @param preProcessed True where a pre-processor feeds the translation: its records are read at its input.
    /// @param users        Every pipeline running the XSLT, and their feeds.
    record Scope(DocRef pipeline, StagedXslt translation, boolean preProcessed, XsltUsers.Users users) {

        /// @return It in words, for the user: the XSLT, and what describing it in place covers.
        String said() {
            return "Its translation XSLT '" + translation.xslt().getName() + "'" + (preProcessed
                    ? " (after a pre-processor: its records are read where the translation reads them)"
                    : "") + ". " + users.said();
        }
    }
}
