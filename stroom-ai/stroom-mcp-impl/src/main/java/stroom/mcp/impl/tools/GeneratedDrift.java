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
import stroom.ai.impl.tools.MergedPipeline.TranslationDoc;
import stroom.ai.impl.tools.generation.CodeDiff;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.validation.XsltVersionHistory;
import stroom.docref.DocRef;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// Whether a build's XSLTs are still what their kept mappings generate (the Stroom MCP server's `tools/builds.py`
/// `_drift`, `_plan_drift` and `lost_mappings`), now the generators are ported: the [MappingDrift] the checks
/// before promotion ([BuildChecks]) use.
class GeneratedDrift implements MappingDrift {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GeneratedDrift.class);

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

    @Override
    public Optional<String> drift(final KeptMappings.KeptXslt kept) {
        final XsltDoc xslt = kept.xslt();
        final String code = NullSafe.string(xslt.getData());
        final XsltGenerator.Generated regenerated;
        try {
            regenerated = keptCode.regenerate(kept.kept().payload(), code);
        } catch (final RuntimeException e) {
            return Optional.of("the mapping kept with its XSLT no longer generates (" + e.getMessage() + ")");
        }
        if (!regenerated.ok()) {
            return Optional.of("the mapping kept with its XSLT no longer generates: " + SampleArguments.pythonList(
                    regenerated.problems().subList(0, Math.min(2, regenerated.problems().size()))));
        }
        if (XsltCode.normalise(regenerated.xslt()).equals(XsltCode.normalise(code))) {
            return Optional.empty();
        }
        final String regenerate = "build_translation_xslt uuid='" + xslt.getUuid() + "' (no mapping) regenerates it "
                                  + "from the mapping kept with it";
        final Optional<Boolean> same = XsltVersionHistory.untouched(xslt.getDescription(), code);
        final String by = XsltVersionHistory.lastSaved(xslt.getDescription(), code)
                .map(XsltVersionHistory.Saved::by)
                .orElse(null);
        if (same.orElse(false)) {
            // Seen in production after 0.16.43: reported as edited by hand, and the agent went looking for the
            // edit.
            return Optional.of("its XSLT was written by an earlier generator (" + NullSafe.nonBlankStringElse(by,
                    "an earlier version") + ") and is unchanged since; the generator now writes its mapping "
                               + "differently (e.g. its style defaults). " + regenerate + ", as the generator writes "
                               + "it now");
        }
        // What differs, so the edit can be carried into the mapping (- what the mapping generates, + the XSLT).
        final String differs = " What differs (- what the mapping generates, + the XSLT): " + String.join(" | ",
                CodeDiff.diff(regenerated.xslt(), code));
        if (same.isPresent()) {
            final String later = NullSafe.isBlankString(by) || by.contains(history.madeBy())
                    ? ""
                    : " (saved by " + by.split(",")[0] + ": the generator's own changes since may show too)";
            return Optional.of("its XSLT was edited by hand since the server saved it, and differs from what its "
                               + "mapping generates: carry the edit into the mapping (rebuild_mapping uuid='"
                               + xslt.getUuid() + "' stream_ids=<the sample streams> does it, proven on the sample; "
                               + "or build_translation_xslt changes=), or accept that the documentation says so."
                               + differs + later);
        }
        return Optional.of("its XSLT differs from what its mapping generates: edited by hand, or written by an "
                           + "earlier version of the generator (it was saved before the server recorded which). "
                           + regenerate + "; carry any hand edit into the mapping first." + differs);
    }

    @Override
    public Optional<String> planDrift(final KeptMappings.KeptXslt kept) {
        final XsltDoc xslt = kept.xslt();
        final String code = NullSafe.string(xslt.getData());
        if (!Optional.of(false).equals(XsltVersionHistory.untouched(xslt.getDescription(), code))) {
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

    @Override
    public List<String> lostMappings(final String pipelineUuid) {
        final List<TranslationDoc> docs;
        try {
            docs = pipelineShapes.merged(pipelineUuid).translationDocs(pipelineUuid);
        } catch (final RuntimeException e) {
            // A safeguard: nothing to say rather than a failed call.
            LOGGER.debug(() -> "Pipeline " + pipelineUuid + " can't be read: " + e.getMessage());
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
