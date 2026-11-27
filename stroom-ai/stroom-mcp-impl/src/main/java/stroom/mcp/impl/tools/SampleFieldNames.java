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

import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.processing.TranslationInputText;
import stroom.ai.impl.tools.sample.SurveyDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// The names of the fields a build's sample records hold where a pipeline's translation reads them: the streams its
/// surveys read (else the pipeline's own sample streams), stepped through the pipeline to the translation's element
/// ([TranslationInputs], as the user: it needs Stepping), each record's input read for its fields
/// ([TranslationInputText#fieldNames]). For a hand-written translation described in place, whose sample fields it
/// never reads are left out of the mapping read back from it ([stroom.ai.impl.tools.generation.FieldsLeftOut]).
class SampleFieldNames {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SampleFieldNames.class);

    /// The most streams stepped.
    static final int MAX_STREAMS = 5;

    private final TranslationBuilds builds;
    private final TranslationInputs inputs;
    private final SampleStreams sampleStreams;

    /// @param builds        The builds: the streams their surveys read.
    /// @param inputs        Reads records at a translation's input, stepped.
    /// @param sampleStreams A pipeline's own sample streams, where the build has no survey.
    @Inject
    SampleFieldNames(final TranslationBuilds builds,
                     final TranslationInputs inputs,
                     final SampleStreams sampleStreams) {
        this.builds = builds;
        this.inputs = inputs;
        this.sampleStreams = sampleStreams;
    }

    /// @param context      The call's context (its time limit).
    /// @param build        The build whose surveyed streams are read.
    /// @param pipelineUuid The pipeline whose translation reads them.
    /// @param element      The translation's element.
    /// @return The fields the records hold at the translation's input, in the order first seen; empty where nothing
    /// could be stepped (no streams, no Stepping permission, or the records hold no named fields).
    List<String> of(final ToolCallContext context,
                    final String build,
                    final String pipelineUuid,
                    final String element) {
        try {
            final List<Long> streams = streams(build, pipelineUuid);
            if (streams.isEmpty()) {
                return List.of();
            }
            final Set<String> names = new LinkedHashSet<>();
            inputs.texts(context, new TranslationInputs.At(pipelineUuid, element), streams,
                            Math.max(1, TranslationInputs.CATALOGUE_RECORDS / streams.size()))
                    .values()
                    .forEach(text -> names.addAll(TranslationInputText.fieldNames(text)));
            return List.copyOf(names);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No sample fields read for build {} at {} of {}: {}", build, element,
                    pipelineUuid, e.getMessage()), e);
            return List.of();
        }
    }

    // The streams the build's surveys read, else the pipeline's sample streams; at most MAX_STREAMS.
    private List<Long> streams(final String build, final String pipelineUuid) {
        final Set<Long> surveyed = new LinkedHashSet<>();
        builds.surveyStatesInBuild(build).forEach(state -> surveyed.addAll(SurveyDoc.streams(state)));
        final List<Long> found = surveyed.isEmpty()
                ? sampleStreams.sampleStreams(pipelineUuid).streamIds()
                : new ArrayList<>(surveyed);
        return found.subList(0, Math.min(MAX_STREAMS, found.size()));
    }
}
