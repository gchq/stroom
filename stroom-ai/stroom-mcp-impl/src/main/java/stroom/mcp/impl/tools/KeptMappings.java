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

import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.documents.KeptMapping;
import stroom.ai.impl.tools.processing.MergedPipeline;
import stroom.ai.impl.tools.processing.MergedPipeline.TranslationDoc;
import stroom.ai.impl.tools.processing.XsltStages;
import stroom.ai.impl.tools.processing.XsltStages.Stage;
import stroom.ai.impl.tools.processing.XsltStages.StagedXslt;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// The mapping a pipeline's own XSLT was generated from, kept with the XSLT (read by [KeptMapping]). Where the
/// pipeline runs several XSLTs of its own, they are read in the order of their stages ([XsltStages], decided with
/// the user 2026-11-25): the translation's first, then the indexing XSLT's, then any other's, so a pre-processor
/// before the translation, or a decorator after it, is never taken for it.
class KeptMappings {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(KeptMappings.class);

    // The stages read first, in this order; any other after them.
    private static final List<Stage> FIRST = List.of(Stage.TRANSLATION, Stage.INDEXING);

    private final PipelineShapes pipelineShapes;
    private final DocumentAccess documentAccess;
    // Tells each XSLT's stage; none in tests that make this themselves (the pipeline's order is read then).
    private XsltStages xsltStages;

    /// @param pipelineShapes Reads a pipeline's XSLTs.
    /// @param documentAccess Reads each XSLT, checking View.
    @Inject
    KeptMappings(final PipelineShapes pipelineShapes, final DocumentAccess documentAccess) {
        this.pipelineShapes = pipelineShapes;
        this.documentAccess = documentAccess;
    }

    /// Tells each XSLT's stage (set by injection; none in tests that make this themselves).
    ///
    /// @param xsltStages The stage classification.
    @Inject
    void setXsltStages(final XsltStages xsltStages) {
        this.xsltStages = xsltStages;
    }

    /// @param pipelineUuid A pipeline.
    /// @return The mapping or index plan kept with the pipeline's own XSLT, or empty.
    /// @throws PipelineUnreadableException if Stroom can't load the pipeline.
    Optional<KeptMapping.Kept> keptMapping(final String pipelineUuid) {
        return keptXslt(pipelineUuid).map(KeptXslt::kept);
    }

    /// @param pipelineUuid A pipeline.
    /// @return The mapping or index plan kept with the pipeline's own XSLT, with the XSLT and the element it is set
    /// on, or empty.
    /// @throws PipelineUnreadableException if Stroom can't load the pipeline.
    Optional<KeptXslt> keptXslt(final String pipelineUuid) {
        final MergedPipeline merged = pipelineShapes.merged(pipelineUuid);
        for (final TranslationDoc entry : byStage(merged, merged.translationDocs(pipelineUuid))) {
            if (entry.inheritedFromTemplate() || !XsltDoc.TYPE.equals(entry.doc().getType())) {
                continue;
            }
            // Deleted, or not this user's to read: no mapping kept with it.
            final Optional<XsltDoc> xslt = documentAccess.read(entry.doc(), XsltDoc.class);
            final Optional<KeptMapping.Kept> found = xslt.flatMap(doc -> KeptMapping.readMapping(doc.getDescription()));
            if (found.isPresent()) {
                return Optional.of(new KeptXslt(found.get(), entry.element(), xslt.get()));
            }
        }
        return Optional.empty();
    }


    // The pipeline's documents with its translation XSLT first, then its indexing XSLT, then the rest in the
    // pipeline's order; as they are where the stages can't be told.
    private List<TranslationDoc> byStage(final MergedPipeline merged, final List<TranslationDoc> docs) {
        if (xsltStages == null) {
            return docs;
        }
        final List<StagedXslt> staged;
        try {
            staged = xsltStages.classify(merged);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("The stages of {} weren't told: {}", merged.pipeline(),
                    e.getMessage()), e);
            return docs;
        }
        final List<TranslationDoc> ordered = new ArrayList<>();
        for (final Stage stage : FIRST) {
            for (final StagedXslt xslt : XsltStages.of(staged, stage)) {
                docs.stream()
                        .filter(doc -> doc.element().equals(xslt.element()) && !ordered.contains(doc))
                        .forEach(ordered::add);
            }
        }
        docs.stream().filter(doc -> !ordered.contains(doc)).forEach(ordered::add);
        return ordered;
    }

    /// The pipeline's own Data Splitter, as processing reads its raw streams with it.
    ///
    /// @param pipelineUuid A pipeline.
    /// @return The converter's XML; empty when the pipeline has none of its own (or several), or it can't be read.
    /// @throws PipelineUnreadableException if Stroom can't load the pipeline.
    Optional<String> dataSplitter(final String pipelineUuid) {
        final List<String> found = new ArrayList<>();
        for (final TranslationDoc entry : pipelineShapes.merged(pipelineUuid).translationDocs(pipelineUuid)) {
            if (TextConverterDoc.TYPE.equals(entry.doc().getType())) {
                documentAccess.read(entry.doc(), TextConverterDoc.class)
                        .filter(converter -> converter.getConverterType() == TextConverterType.DATA_SPLITTER)
                        .map(TextConverterDoc::getData)
                        .filter(NullSafe::isNonBlankString)
                        .ifPresent(found::add);
            }
        }
        return found.size() == 1
                ? Optional.of(found.getFirst())
                : Optional.empty();
    }

    // --------------------------------------------------------------------------------


    /// A mapping kept with a pipeline's own XSLT.
    ///
    /// @param kept    The mapping: its kind (translation, index or cef) and payload.
    /// @param element The element the XSLT is set on.
    /// @param xslt    The XSLT.
    record KeptXslt(KeptMapping.Kept kept, String element, XsltDoc xslt) {

    }
}
