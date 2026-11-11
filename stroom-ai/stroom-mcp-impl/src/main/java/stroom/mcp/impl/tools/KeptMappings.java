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
import stroom.ai.impl.tools.MergedPipeline.TranslationDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// The mapping a pipeline's own XSLT was generated from, kept with the XSLT (read by [KeptMapping]).
class KeptMappings {

    private final PipelineShapes pipelineShapes;
    private final DocumentAccess documentAccess;

    /// @param pipelineShapes Reads a pipeline's XSLTs.
    /// @param documentAccess Reads each XSLT, checking View.
    @Inject
    KeptMappings(final PipelineShapes pipelineShapes, final DocumentAccess documentAccess) {
        this.pipelineShapes = pipelineShapes;
        this.documentAccess = documentAccess;
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
        for (final TranslationDoc entry : pipelineShapes.merged(pipelineUuid).translationDocs(pipelineUuid)) {
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
