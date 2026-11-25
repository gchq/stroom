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
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.documents.KeptMapping;
import stroom.ai.impl.tools.generation.CodeDiff;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.ModuleChecks;
import stroom.ai.impl.tools.generation.SourceTypeModule;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.processing.MergedPipeline.TranslationDoc;
import stroom.ai.impl.tools.validation.XsltVersionHistory;
import stroom.docref.DocRef;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Whether a build's XSLTs are still what their kept mappings generate, for the checks before promotion ([BuildChecks])
/// and the Field mapping section: each XSLT is regenerated from its mapping and compared.
class GeneratedDrift {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GeneratedDrift.class);
    // How the drift of an XSLT edited since its mapping was kept starts: saved as written by hand, or changed in
    // Stroom since.
    private static final String HAND_SAVED = "its XSLT was saved as written by hand";
    private static final String EDITED = "its XSLT was edited by hand";
    // The UUID an import's href names.
    private static final Pattern HREF_UUID = Pattern.compile("uuid='([^']+)'");

    private final KeptCode keptCode;
    private final XsltHistory history;
    private final PipelineShapes pipelineShapes;
    private final DocumentAccess documentAccess;
    private final TranslationDocs translationDocs;

    /// @param keptCode        What a kept mapping or plan generates now.
    /// @param history         What the tools' saves say made them.
    /// @param pipelineShapes  Reads a pipeline's XSLTs.
    /// @param documentAccess  Reads each XSLT, as the user.
    /// @param translationDocs Says why an XSLT's kept mapping is gone.
    @Inject
    GeneratedDrift(final KeptCode keptCode,
                   final XsltHistory history,
                   final PipelineShapes pipelineShapes,
                   final DocumentAccess documentAccess,
                   final TranslationDocs translationDocs) {
        this.keptCode = keptCode;
        this.history = history;
        this.pipelineShapes = pipelineShapes;
        this.documentAccess = documentAccess;
        this.translationDocs = translationDocs;
    }

    /// @param kept A translation mapping kept with its XSLT.
    /// @return Why the XSLT is no longer what its mapping generates (edited by hand, an earlier generator, or a
    /// mapping that no longer generates), or empty if it is.
    public Optional<String> drift(final KeptMappings.KeptXslt kept) {
        final XsltDoc xslt = kept.xslt();
        if (inducted(kept)) {
            return Optional.empty();
        }
        final String code = NullSafe.string(xslt.getData());
        final XsltGenerator.Generated regenerated;
        try {
            regenerated = keptCode.regenerate(kept.kept().payload(), code);
        } catch (final RuntimeException e) {
            return Optional.of("the mapping kept with its XSLT no longer generates (" + e.getMessage() + ")");
        }
        if (!regenerated.ok()) {
            return Optional.of("the mapping kept with its XSLT no longer generates: " + ToolText.list(
                    regenerated.problems().subList(0, Math.min(2, regenerated.problems().size()))));
        }
        if (XsltCode.normalise(regenerated.xslt()).equals(XsltCode.normalise(code))) {
            return Optional.empty();
        }
        final String regenerate = "build_translation_xslt uuid='" + xslt.getUuid() + "' (no mapping) regenerates it "
                                  + "from the mapping kept with it";
        final Optional<Boolean> same = XsltVersionHistory.untouched(xslt.getDescription(), code);
        final Optional<XsltVersionHistory.Saved> saved = XsltVersionHistory.lastSaved(xslt.getDescription(), code);
        final String by = saved.map(XsltVersionHistory.Saved::by).orElse(null);
        // What differs, so the edit can be carried into the mapping (- what the mapping generates, + the XSLT).
        final String differs = " What differs (- what the mapping generates, + the XSLT): " + String.join(" | ",
                CodeDiff.diff(regenerated.xslt(), code));
        if (same.orElse(false) && saved.map(XsltVersionHistory.Saved::hand).orElse(false)) {
            // A hand edit, never an earlier generator's code to regenerate, which would undo the edit (seen in testing:
            // TWITTER's mentions and links, saved with save_xslt, taken for an earlier generator's code, and the
            // documentation generated from the mapping without them).
            return Optional.of(HAND_SAVED + " (save_xslt) since its mapping was kept, and "
                               + "differs from what that mapping generates: the mapping no longer says what it "
                               + "writes. Carry the edit into the mapping: build_translation_xslt uuid='"
                               + xslt.getUuid() + "' changes= (repeat: true writes one element per value; "
                               + "data_parent writes Data in a named Data), or rebuild_mapping uuid='"
                               + xslt.getUuid() + "' stream_ids=<the sample streams> (it reads back an XSLT laid out "
                               + "as the generator writes one). Never regenerate it from the mapping as it is: that "
                               + "undoes the edit." + differs);
        }
        if (same.orElse(false)) {
            // Seen in production after a generator change: reported as edited by hand, and the agent went looking for
            // the edit.
            return Optional.of("its XSLT was written by an earlier generator (" + NullSafe.nonBlankStringElse(by,
                    "an earlier version") + ") and is unchanged since; the generator now writes its mapping "
                               + "differently (e.g. its style defaults). " + regenerate + ", as the generator writes "
                               + "it now");
        }
        if (same.isPresent()) {
            final String later = NullSafe.isBlankString(by) || by.contains(history.madeBy())
                    ? ""
                    : " (saved by " + by.split(",")[0] + ": the generator's own changes since may show too)";
            return Optional.of(EDITED + " since the server saved it, and differs from what its "
                               + "mapping generates: carry the edit into the mapping (rebuild_mapping uuid='"
                               + xslt.getUuid() + "' stream_ids=<the sample streams> does it, proven on the sample; "
                               + "or build_translation_xslt changes=), or accept that the documentation says so."
                               + differs + later);
        }
        return Optional.of("its XSLT differs from what its mapping generates: edited by hand, or written by an "
                           + "earlier version of the generator (it was saved before the server recorded which). "
                           + regenerate + "; carry any hand edit into the mapping first." + differs);
    }

    /// @param kept A translation mapping kept with its XSLT.
    /// @return True if the mapping was read from this very XSLT, written by hand, when it was described in place
    /// ([InPlaceInduction]): the XSLT is the authority and never what the mapping generates, so no drift is said. A
    /// copy of it (a new version's) carries the block naming the original, and is checked as any other.
    static boolean inducted(final KeptMappings.KeptXslt kept) {
        return JsonValues.mapOrEmpty(kept.kept().payload().get(InPlaceInduction.INDUCTED))
                .get(InPlaceInduction.XSLT) instanceof final String uuid
               && uuid.equals(kept.xslt().getUuid());
    }

    /// The drift of an XSLT edited since its mapping was kept, by hand in Stroom or saved as written by hand
    /// (save_xslt): its mapping no longer says what it writes, so documentation generated from it would be wrong.
    ///
    /// @param kept A translation mapping kept with its XSLT.
    /// @return Why the XSLT is no longer what its mapping generates, when it was edited; else empty (it is, or
    /// only the generator changed since).
    public Optional<String> editDrift(final KeptMappings.KeptXslt kept) {
        return drift(kept).filter(why -> why.startsWith(HAND_SAVED) || why.startsWith(EDITED));
    }

    /// @param kept An index or CEF plan kept with its XSLT.
    /// @return Why the XSLT was edited by hand since it was saved from its plan, or empty if it wasn't.
    public Optional<String> planDrift(final KeptMappings.KeptXslt kept) {
        final XsltDoc xslt = kept.xslt();
        final String code = NullSafe.string(xslt.getData());
        // Saved by hand (save_xslt) counts as an edit too.
        if (!Optional.of(false).equals(XsltVersionHistory.asGenerated(xslt.getDescription(), code))) {
            return Optional.empty();
        }
        final String kind = kept.kept().kind();
        final String base = keptCode.keptCode(kind, kept.kept().payload()).orElse(null);
        if (base != null && XsltCode.normalise(base).equals(XsltCode.normalise(code))) {
            return Optional.empty();
        }
        final String differs = base == null
                ? ""
                : " What differs (- what the plan generates, + the XSLT): " + String.join(" | ",
                        CodeDiff.diff(base, code));
        return Optional.of("its XSLT was edited by hand since the server saved it, and differs from what its " + kind
                           + " plan generates: " + HandEditGate.CARRY.get(kind) + ", as a save from the plan that "
                           + "undoes the edit is refused." + differs);
    }

    /// The aggregated feed's modules an XSLT imports (its common module, and the helpers that imports), each edited by
    /// hand in Stroom since the tools saved it ([ModuleEdits]), for the checks before promotion: a module runs as
    /// edited, and a later build that adds to it asks the user whether to keep the edit.
    ///
    /// @param code An XSLT's code (a source type group's).
    /// @return Why, for each module edited, by the module's UUID; none for an XSLT importing no module.
    public Map<String, String> moduleEdits(final String code) {
        final Map<String, String> out = new LinkedHashMap<>();
        final List<String> uuids = new ArrayList<>();
        ModuleChecks.hrefs(code).forEach(href -> uuidOf(href).ifPresent(uuids::add));
        for (int i = 0; i < uuids.size(); i++) {
            final String uuid = uuids.get(i);
            final Optional<XsltDoc> module = readModule(uuid);
            final Optional<ModuleChecks.Marker> marker = module.flatMap(doc -> ModuleChecks.marker(
                    NullSafe.string(doc.getData())));
            if (module.isEmpty() || marker.isEmpty() || out.containsKey(uuid)) {
                continue;
            }
            final XsltDoc doc = module.get();
            final Optional<ModuleEdits.Edit> edit;
            if (SourceTypeModule.COMMON.equals(marker.get().kind())) {
                // The helpers it imports are read too.
                ModuleChecks.hrefs(doc.getData()).forEach(href -> uuidOf(href).filter(next -> !uuids.contains(next))
                        .ifPresent(uuids::add));
                edit = commonEdit(uuid, doc);
            } else if (ModuleChecks.HELPERS.equals(marker.get().kind())) {
                edit = ModuleEdits.helpers(uuid, doc);
            } else {
                edit = Optional.empty();
            }
            edit.ifPresent(found -> out.put(uuid, "its imported module '" + found.name() + "' was edited by hand in "
                                                  + "Stroom since the tools saved it" + found.by() + ": "
                                                  + found.changed() + ". It runs as edited; a later build that adds "
                                                  + "to it asks the user whether to keep the edit (read into its "
                                                  + "mapping) or discard it"));
        }
        return out;
    }

    // A common module's edit, against what its kept mapping generates now.
    private Optional<ModuleEdits.Edit> commonEdit(final String uuid, final XsltDoc doc) {
        final Optional<KeptMapping.Kept> kept = KeptMapping.readMapping(doc.getDescription())
                .filter(found -> KeptMapping.TRANSLATION.equals(found.kind()));
        if (kept.isEmpty()) {
            return Optional.empty();
        }
        try {
            final TranslationMapping mapping = TranslationMapping.parse(kept.get().payload().get("mapping"));
            final Function<TranslationMapping, String> generate = each -> {
                final Map<String, Object> payload = new LinkedHashMap<>(kept.get().payload());
                payload.put("mapping", each.toJson(DumpMode.COMPACT));
                return keptCode.regenerate(payload, doc.getData()).xslt();
            };
            return ModuleEdits.common(uuid, doc, mapping, generate);
        } catch (final RuntimeException e) {
            // A safeguard: nothing to say rather than a failed check.
            LOGGER.debug(() -> LogUtil.message("Module {} not compared with its mapping: {}", uuid, e.getMessage()));
            return Optional.empty();
        }
    }

    private Optional<XsltDoc> readModule(final String uuid) {
        try {
            return documentAccess.read(new DocRef(XsltDoc.TYPE, uuid), XsltDoc.class);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Module {} can't be read: {}", uuid, e.getMessage()));
            return Optional.empty();
        }
    }

    private static Optional<String> uuidOf(final String href) {
        final Matcher matcher = HREF_UUID.matcher(NullSafe.string(href));
        return matcher.find()
                ? Optional.of(matcher.group(1))
                : Optional.empty();
    }

    /// @param pipelineUuid A pipeline whose own XSLTs keep no mapping.
    /// @return For each of them saved with a mapping that its Documentation tab no longer holds: why.
    public List<String> lostMappings(final String pipelineUuid) {
        final List<TranslationDoc> docs;
        try {
            docs = pipelineShapes.merged(pipelineUuid).translationDocs(pipelineUuid);
        } catch (final RuntimeException e) {
            // A safeguard: nothing to say rather than a failed call.
            LOGGER.debug(() -> LogUtil.message("Pipeline {} can't be read: {}", pipelineUuid, e.getMessage()));
            return List.of();
        }
        final List<String> out = new ArrayList<>();
        for (final TranslationDoc entry : docs) {
            if (entry.inheritedFromTemplate() || !XsltDoc.TYPE.equals(entry.doc().getType())) {
                continue;
            }
            documentAccess.read(entry.doc(), XsltDoc.class).flatMap(xslt -> translationDocs.lostMapping(
                            new DocRef(XsltDoc.TYPE, xslt.getUuid(), xslt.getName()), xslt.getDescription()))
                    .ifPresent(out::add);
        }
        return out;
    }
}
