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
import stroom.pipeline.shared.XsltDoc;

import jakarta.inject.Inject;

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
        for (final TranslationDoc entry : pipelineShapes.merged(pipelineUuid).translationDocs(pipelineUuid)) {
            if (entry.inheritedFromTemplate() || !XsltDoc.TYPE.equals(entry.doc().getType())) {
                continue;
            }
            // Deleted, or not this user's to read: no mapping kept with it.
            final Optional<KeptMapping.Kept> found = documentAccess.read(entry.doc(), XsltDoc.class)
                    .flatMap(xslt -> KeptMapping.readMapping(xslt.getDescription()));
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }
}
