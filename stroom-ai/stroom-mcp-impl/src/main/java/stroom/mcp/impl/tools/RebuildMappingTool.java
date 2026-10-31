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

import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.MergedPipeline.TranslationDoc;
import stroom.ai.impl.tools.ToolArgs;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.generation.RebuiltMapping;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.generation.XsltReader;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.pipeline.shared.XsltDoc;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/// `rebuild_mapping` - rebuilds the mapping kept with a translation XSLT from the XSLT itself, when it was lost (its
/// Documentation tab cleared) or no longer matches the XSLT (changed by hand in Stroom). The mapping is read back from
/// the XSLT ([XsltReader]), the XSLT regenerated from it, and both stepped over the sample streams: it is saved only
/// when every record's output is the same (or the user accepts the differences shown), after the user confirms, and the
/// XSLT is then the one regenerated from it.
class RebuildMappingTool extends AbstractBuiltInTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RebuildMappingTool.class);

    static final String NAME = "rebuild_mapping";
    static final String UUID_ARG = "uuid";
    static final String STREAM_IDS_ARG = "stream_ids";
    static final String PIPELINE_UUID_ARG = "pipeline_uuid";
    static final String CHANGE_ARG = "change";
    static final String ACCEPT_DIFFERENCES_ARG = "accept_differences";
    static final String ACCEPT_UNREAD_ARG = "accept_unread";

    /// The most records stepped each way.
    static final int PROVE_RECORDS = 200;

    private final TranslationDocs translationDocs;
    private final WriteGuard guard;
    private final EventVersions eventVersions;
    private final Consent consent;
    private final PipelineStepping stepping;
    private final PipelineShapes pipelineShapes;
    private final PipelineIndex pipelineIndex;
    private final SampleStreams sampleStreams;
    private final Provider<DocFinder> docFinderProvider;
    private final DocumentAccess documentAccess;

    @Inject
    RebuildMappingTool(final TranslationDocs translationDocs,
                       final WriteGuard guard,
                       final EventVersions eventVersions,
                       final Consent consent,
                       final PipelineStepping stepping,
                       final PipelineShapes pipelineShapes,
                       final PipelineIndex pipelineIndex,
                       final SampleStreams sampleStreams,
                       final Provider<DocFinder> docFinderProvider,
                       final DocumentAccess documentAccess) {
        this.translationDocs = translationDocs;
        this.guard = guard;
        this.eventVersions = eventVersions;
        this.consent = consent;
        this.stepping = stepping;
        this.pipelineShapes = pipelineShapes;
        this.pipelineIndex = pipelineIndex;
        this.sampleStreams = sampleStreams;
        this.docFinderProvider = docFinderProvider;
        this.documentAccess = documentAccess;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public boolean isReadOnly() {
        // It saves the mapping and the XSLT regenerated from it, once the user confirms.
        return false;
    }

    @Override
    public boolean confirmsItself() {
        // The user confirms the save in a form, with what the rebuilt mapping keeps, adds and removes.
        return true;
    }

    @Override
    public boolean usesRunSlot() {
        // It steps up to 200 records twice.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .unlockedBy(AppPermission.STEPPING_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.EDIT)
                .note("The XSLT, its pipeline and the XSLTs it imports are read as the user, the pipeline stepped "
                      + "as the user (Stepping), and the XSLT saved as the user (Edit), only once they confirm.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Rebuild the mapping kept with a translation XSLT, from the XSLT itself: when it was "
                             + "lost (its Documentation tab cleared; build_status says so) or no longer matches the "
                             + "XSLT (changed by hand in Stroom; build_status shows what differs). With the kept "
                             + "mapping, every element that still reads as it generates keeps its entry exactly, "
                             + "and the hand edits become new or removed entries; without one, the mapping is read "
                             + "from the XSLT whole (fields, key=value extractions, maps, time formats, defaults, "
                             + "reference data lookups, dictionaries, the shared templates and functions of the "
                             + "XSLTs it imports, read from Stroom by name; anything else as an xpath entry). The "
                             + "XSLT regenerated from it is then stepped beside the XSLT over the pipeline's "
                             + "original sample streams (or stream_ids given), and only when every record's output "
                             + "is the same, and the user confirms, are the mapping and the regenerated XSLT saved. "
                             + "Then step_sample, and write_documentation again. Reads Stroom until it saves; saves "
                             + "only an XSLT in a build (a working copy for a promoted one).")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(UUID_ARG, "The translation XSLT whose mapping to rebuild.")
                        .addProperty(STREAM_IDS_ARG, BuildTranslationXsltTool.streamIdsSchema(
                                "The raw streams to prove the rebuilt mapping on: both the XSLT and the one "
                                + "regenerated from it are stepped over them, and the mapping is only saved when "
                                + "every record's output is the same. Leave out for the pipeline's original sample "
                                + "streams (else its feed's newest raw streams); the reply says which."))
                        .addStringProperty(PIPELINE_UUID_ARG, "The pipeline that runs the XSLT; found from the XSLT "
                                                              + "when only one does.")
                        .addStringProperty(CHANGE_ARG, "For the version history: what was rebuilt and why, e.g. "
                                                       + "'Mapping rebuilt after the Documentation tab was "
                                                       + "cleared'.")
                        .addBooleanProperty(ACCEPT_DIFFERENCES_ARG, "Save even though the regenerated XSLT writes "
                                                                    + "some records differently (shown in "
                                                                    + "differences): only when the user, shown "
                                                                    + "them, accepts what the mapping writes "
                                                                    + "instead.")
                        .addBooleanProperty(ACCEPT_UNREAD_ARG, "Rebuild even though parts of the XSLT's events "
                                                               + "couldn't be read (shown in unread), so the "
                                                               + "mapping leaves them out: only when the user, "
                                                               + "shown them, agrees to lose them.")
                        .required(UUID_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            return JsonResults.of(rebuild(call, context));
        } catch (final ToolRefusal | PipelineUnreadableException e) {
            return JsonResults.refused(e.getMessage());
        }
    }

    private Map<String, Object> rebuild(final ToolCall call, final ToolCallContext context) {
        final String uuid = stringArg(call, UUID_ARG);
        if (uuid == null) {
            throw new ToolRefusal("Give uuid: the translation XSLT whose mapping to rebuild.");
        }
        final XsltDoc xslt = translationDocs.readXslt(uuid);
        final DocRef ref = new DocRef(XsltDoc.TYPE, uuid, xslt.getName());
        guard.checkManaged(context.session(), ref);
        final String code = NullSafe.string(xslt.getData());
        final Optional<KeptMapping.Kept> kept = KeptMapping.readMapping(xslt.getDescription());
        if (kept.isPresent() && !KeptMapping.TRANSLATION.equals(kept.get().kind())) {
            throw new ToolRefusal("XSLT '" + xslt.getName() + "' keeps a " + kept.get().kind() + " plan, not a "
                                  + "translation mapping: rebuild_mapping is for translations (for CEF, "
                                  + "draft_cef_mapping pipeline_uuid=... reviews what it writes)");
        }
        final String version = eventVersions.translationVersion(kept.map(k -> KeptCode.keptVersion(k.payload()))
                .orElse(null), code);
        final EventSchema schema = eventVersions.eventSchema(version);
        final TranslationMapping base = kept.map(k -> TranslationMapping.parse(k.payload().get("mapping")))
                .orElse(null);
        final String baseCode = base == null
                ? null
                : XsltGenerator.generate(base, schema, version).xslt();
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("xslt", refMap(ref));
        if (baseCode != null && XsltCode.normalise(baseCode).equals(XsltCode.normalise(code))) {
            result.put("status", "in_step");
            result.put("hint", "The XSLT is what its kept mapping generates: nothing to rebuild.");
            return result;
        }
        final RebuiltMapping rebuilt = XsltReader.rebuild(code, base, baseCode, base == null
                ? imported(code)
                : Map.of());
        // Nothing new or removed: the XSLT reads as the mapping, written by an earlier generator, not changed by
        // hand.
        final boolean upgraded = base != null && rebuilt.added().isEmpty() && rebuilt.removed().isEmpty()
                                 && rebuilt.raw().isEmpty();
        result.put("mode", upgraded
                ? "written by an earlier generator"
                : base != null
                        ? "out of step with the kept mapping"
                        : "mapping lost");
        result.put("rebuilt", rebuilt.summary());
        if (rebuilt.mapping() == null || !rebuilt.problems().isEmpty()) {
            result.put("saved", null);
            // Seen in testing: an XSLT rewritten by hand outside the generator's layout read back as nothing, and the
            // agent took it for an empty mapping and the edit for one no mapping can express.
            final boolean unlaid = rebuilt.notes().stream().anyMatch(note -> note.startsWith("no record template"));
            result.put("hint", unlaid && base != null
                    ? "The XSLT isn't laid out as the generator writes one (a template per rule), so nothing in it "
                      + "could be read back: the kept mapping is still there. Carry the hand edit into it with "
                      + "build_translation_xslt uuid=<this XSLT> changes= only the entries that change (repeat: "
                      + "true writes one element per value; data_parent writes Data in a named Data; an xpath "
                      + "computes a value, e.g. one with the mentions taken out), then step_sample: the generated "
                      + "XSLT replaces the hand-written one."
                    : "The mapping couldn't be read back: give build_translation_xslt the whole mapping "
                      + "instead (draft_translation_mapping drafts one from the samples).");
            return result;
        }
        if (!rebuilt.lost().isEmpty() && !ToolArgs.flag(call, ACCEPT_UNREAD_ARG)) {
            // Seen in testing: a construct the reader doesn't know (an xsl:iterate making users distinct, say) was left
            // out, and the proof by stepping showed nothing when no sample record reached it.
            result.put("saved", null);
            result.put("unread", rebuilt.lost());
            result.put("hint", "Parts of the XSLT's events couldn't be read back (unread): a mapping rebuilt from "
                               + "it leaves them out, and the regenerated XSLT would lose them for the whole feed, "
                               + "whether or not the sample's records reach them. Nothing was saved. Show the user "
                               + "what they are: to keep the XSLT as it is, leave the mapping as it is; to rebuild "
                               + "without them all the same, call again with " + ACCEPT_UNREAD_ARG + "=true.");
            return result;
        }
        final XsltGenerator.Generated regenerated = XsltGenerator.generate(rebuilt.mapping(), schema, version);
        if (!regenerated.ok()) {
            result.put("saved", null);
            result.put("problems", regenerated.problems().subList(0, Math.min(10, regenerated.problems().size())));
            result.put("hint", "The mapping read back from the XSLT doesn't generate: the XSLT writes something a "
                               + "mapping can't (the problems say what). Fix the XSLT or give build_translation_xslt "
                               + "the whole mapping.");
            return result;
        }
        final String pipelineUuid = stringArg(call, PIPELINE_UUID_ARG);
        final Runs runs = pipelineUuid != null
                ? new Runs(pipelineUuid, elementIn(pipelineUuid, uuid, xslt.getName()))
                : pipelineOf(uuid);
        List<Long> ids = StartOnboardingTool.streamIds(call, STREAM_IDS_ARG);
        String samples = "the streams given";
        if (ids.isEmpty()) {
            final SampleStreams.Found found = sampleStreams.sampleStreams(runs.pipelineUuid());
            ids = found.streamIds();
            samples = found.which();
        }
        final Proof proof = prove(context, runs, ids, regenerated.xslt());
        final Map<String, Object> proven = new LinkedHashMap<>();
        proven.put("records", proof.records());
        proven.put("streams", ids);
        proven.put("which", samples);
        result.put("proven_on", proven);
        if (proof.records() == 0) {
            result.put("saved", null);
            result.put("hint", "Stepping gave no records: check stream_ids are the pipeline's raw streams.");
            return result;
        }
        final boolean accept = ToolArgs.flag(call, ACCEPT_DIFFERENCES_ARG);
        if (!proof.differences().isEmpty()) {
            result.put("differences", proof.differences().subList(0, Math.min(20, proof.differences().size())));
            if (!accept) {
                result.put("saved", null);
                result.put("hint", "The regenerated XSLT writes these records differently from the XSLT: nothing "
                                   + "was saved. Usually an edit a mapping can't express (an element written when "
                                   + "its value is empty, a choose inside an event). Show the user the differences: "
                                   + "to keep the XSLT as it is, leave the mapping as it is; if they accept what the "
                                   + "mapping writes, call again with accept_differences=true.");
                return result;
            }
        }
        return save(call, context, result, new Rebuild(ref, base, upgraded, rebuilt, regenerated.xslt(), proof));
    }

    // Asked, then saved: the mapping, and the XSLT regenerated from it. The hand edit is carried by the rebuilt
    // mapping, proven on the sample (or its differences accepted).
    private Map<String, Object> save(final ToolCall call,
                                     final ToolCallContext context,
                                     final Map<String, Object> result,
                                     final Rebuild rebuild) {
        final boolean lost = rebuild.base() == null;
        final String mode = lost
                ? "mapping lost"
                : rebuild.upgraded()
                        ? "the mapping unchanged: the XSLT written by an earlier generator"
                        : "carrying hand edits into the mapping";
        final String said = NullSafe.nonBlankStringElse(stringArg(call, CHANGE_ARG), lost
                ? "Mapping rebuilt from the XSLT, after its Documentation tab was cleared"
                : rebuild.upgraded()
                        ? "Regenerated from its mapping (written by an earlier generator)"
                        : "Mapping rebuilt from the XSLT, carrying in its hand edits");
        final RebuiltMapping rebuilt = rebuild.rebuilt();
        final List<Map<String, Object>> differences = rebuild.proof().differences();
        final Map<String, Object> details = new LinkedHashMap<>();
        details.put("kept entries", rebuilt.reused());
        details.put("new entries", first(rebuilt.added(), 20));
        details.put("removed entries", first(rebuilt.removed(), 20));
        details.put("kept as xpath", first(rebuilt.raw(), 20));
        if (!rebuilt.importedCalls().isEmpty()) {
            details.put("calling imported functions", first(rebuilt.importedCalls(), 20));
        }
        if (!differences.isEmpty()) {
            details.put("differences", differences.subList(0, Math.min(20, differences.size())).stream()
                    .map(d -> d.get("path"))
                    .toList());
        }
        consent.require(call, context, Consent.Request.confirmation(NAME,
                "Save the mapping rebuilt from XSLT '" + rebuild.ref().getName() + "' (" + mode + "), and the XSLT "
                + "regenerated from it: " + (differences.isEmpty()
                        ? "it wrote the same output as the XSLT for all " + rebuild.proof().records()
                          + " records stepped"
                        : differences.size() + " output paths differ, as shown"), details));
        final Map<String, Object> saved = translationDocs.updateXslt(call, context, rebuild.ref().getUuid(),
                rebuild.regenerated(), null, TranslationDocs.Source.of(rebuilt.mapping()), said, true, null);
        final Map<String, Object> summary = new LinkedHashMap<>();
        for (final String key : List.of("type", "uuid", "name", "version")) {
            summary.put(key, saved.get(key));
        }
        result.put("saved", summary);
        result.put("next", "step_sample the pipeline over its sample streams (its code changed to what the mapping "
                           + "generates), then write_documentation again: its Field mapping section comes from the "
                           + "rebuilt mapping. From now on, change the mapping (build_translation_xslt changes=), not "
                           + "the XSLT.");
        return result;
    }

    // Both XSLTs stepped over the streams, and the paths whose values differ, most records first.
    private Proof prove(final ToolCallContext context,
                        final Runs runs,
                        final List<Long> ids,
                        final String regenerated) {
        stepping.checkCanStep();
        final SteppedPipeline loaded = stepping.load(runs.pipelineUuid());
        final PipelineStepping.Outputs before = PipelineStepping.withinTimeLimit(context, () ->
                stepping.outputs(context, loaded, ids, runs.element(), null, PROVE_RECORDS));
        final PipelineStepping.Outputs after = PipelineStepping.withinTimeLimit(context, () ->
                stepping.outputs(context, loaded, ids, runs.element(), Map.of(runs.element(), regenerated),
                        PROVE_RECORDS));
        stepping.audit(context, ids, "Stepped stream(s) through " + loaded.name() + " with the " + NAME + " tool");
        final Map<String, Map<String, Object>> differences = new LinkedHashMap<>();
        before.outputs().forEach((record, oldXml) -> {
            final Map<String, List<String>> old = EventXml.fieldValues(NullSafe.string(oldXml));
            final Map<String, List<String>> now = EventXml.fieldValues(NullSafe.string(
                    after.outputs().getOrDefault(record, "")));
            final Set<String> paths = new TreeSet<>(old.keySet());
            paths.addAll(now.keySet());
            for (final String path : paths) {
                if (!Objects.equals(old.get(path), now.get(path))) {
                    final Map<String, Object> entry = differences.computeIfAbsent(path, key -> {
                        final Map<String, Object> made = new LinkedHashMap<>();
                        made.put("path", key);
                        made.put("records", 0);
                        made.put("example", null);
                        return made;
                    });
                    entry.put("records", (Integer) entry.get("records") + 1);
                    if (entry.get("example") == null) {
                        final Map<String, Object> example = new LinkedHashMap<>();
                        example.put("record", record);
                        example.put("xslt", old.get(path));
                        example.put("rebuilt", now.get(path));
                        entry.put("example", example);
                    }
                }
            }
        });
        final List<Map<String, Object>> sorted = new ArrayList<>(differences.values());
        sorted.sort(Comparator.comparingInt((Map<String, Object> d) -> -(Integer) d.get("records")));
        return new Proof(before.outputs().size(), sorted);
    }

    // The pipeline that runs the XSLT as its own (not through a template), and the element it is set on.
    private Runs pipelineOf(final String uuid) {
        final List<Runs> found = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        for (final PipelineIndex.Entry entry : pipelineIndex.read().values()) {
            if (!ownLayerNames(entry, uuid)) {
                continue;
            }
            try {
                final Optional<String> element = pipelineShapes.merged(entry.uuid()).translationDocs(entry.uuid())
                        .stream()
                        .filter(doc -> !doc.inheritedFromTemplate() && uuid.equals(doc.doc().getUuid()))
                        .map(TranslationDoc::element)
                        .findFirst();
                if (element.isPresent()) {
                    found.add(new Runs(entry.uuid(), element.get()));
                    names.add(ToolText.repr(entry.name()));
                }
            } catch (final PipelineUnreadableException e) {
                LOGGER.debug(() -> LogUtil.message("Pipeline {} can't be read: {}", entry.uuid(), e.getMessage()));
            }
        }
        if (found.size() != 1) {
            throw new ToolRefusal("Give pipeline_uuid: the pipeline that runs this XSLT, to step it over the sample ("
                                  + (found.isEmpty()
                    ? "none found"
                    : "found " + String.join(", ", names)) + ")");
        }
        return found.getFirst();
    }

    // The element of the pipeline given that runs the XSLT.
    private String elementIn(final String pipelineUuid, final String uuid, final String name) {
        return pipelineShapes.merged(pipelineUuid).translationDocs(pipelineUuid).stream()
                .filter(doc -> uuid.equals(doc.doc().getUuid()))
                .map(TranslationDoc::element)
                .findFirst()
                .orElseThrow(() -> new ToolRefusal("Pipeline " + pipelineUuid + " doesn't run XSLT '" + name + "'"));
    }

    // The XSLTs a lost mapping's XSLT imports, by name as Stroom resolves them: what a shared template writes is in
    // its own XSLT.
    private Map<String, String> imported(final String code) {
        final Map<String, String> imported = new LinkedHashMap<>();
        for (final String href : new LinkedHashSet<>(XsltReader.read(code).imports())) {
            final List<DocRef> found = NullSafe.list(docFinderProvider.get().findByName(XsltDoc.TYPE, href, false))
                    .stream()
                    .filter(ref -> href.equals(ref.getName()))
                    .filter(documentAccess::canView)
                    .toList();
            if (found.size() == 1) {
                // Listed, but gone or unreadable: as if not found.
                documentAccess.read(found.getFirst(), XsltDoc.class)
                        .ifPresent(doc -> imported.put(href, NullSafe.string(doc.getData())));
            }
        }
        return imported;
    }

    // Whether a pipeline's own layer names the XSLT (where Stroom's content search found it).
    private static boolean ownLayerNames(final PipelineIndex.Entry entry, final String uuid) {
        if (entry.own() == null) {
            return false;
        }
        try {
            return JsonUtil.writeValueAsString(entry.own(), false).contains(uuid);
        } catch (final RuntimeException e) {
            return false;
        }
    }

    private static Map<String, Object> refMap(final DocRef ref) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", ref.getType());
        map.put("uuid", ref.getUuid());
        map.put("name", ref.getName());
        return map;
    }

    private static <T> List<T> first(final List<T> items, final int max) {
        return items.subList(0, Math.min(max, items.size()));
    }


    // --------------------------------------------------------------------------------


    // The pipeline that runs the XSLT, and the element it is set on.
    private record Runs(String pipelineUuid, String element) {

    }

    // The records stepped, and the output paths that differ.
    private record Proof(int records, List<Map<String, Object>> differences) {

    }

    // What is saved, once confirmed.
    private record Rebuild(DocRef ref,
                           TranslationMapping base,
                           boolean upgraded,
                           RebuiltMapping rebuilt,
                           String regenerated,
                           Proof proof) {

    }
}
