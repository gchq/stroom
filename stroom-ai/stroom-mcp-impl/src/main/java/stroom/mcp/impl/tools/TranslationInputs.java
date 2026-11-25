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
import stroom.ai.impl.tools.processing.TranslationInputText;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// The records a build reads at its translation's input (decided with the user 2026-11-25): a hand-written
/// translation described in place whose pipeline runs a pre-processor before it is surveyed, and its catalogue's
/// conditions checked, against what the pre-processor writes, not what the parser does. Each stream is stepped
/// through the pipeline (as the user, which needs the Stepping permission) and the translation element's input
/// captured a record at a time ([TranslationInputText]). A build without a pre-processor reads its raw streams as
/// before: the parser's output is the translation's input.
class TranslationInputs {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TranslationInputs.class);

    /// The most records stepped a stream for the survey.
    static final int SURVEY_RECORDS = 100;
    /// The most records stepped for the catalogue's check, over all its streams.
    static final int CATALOGUE_RECORDS = 200;

    private final Provider<BuildRecords> buildRecords;
    private final PipelineStepping stepping;

    /// @param buildRecords The build records: how the build started.
    /// @param stepping     Steps the pipeline, as the user.
    @Inject
    TranslationInputs(final Provider<BuildRecords> buildRecords, final PipelineStepping stepping) {
        this.buildRecords = buildRecords;
        this.stepping = stepping;
    }

    /// @param build A build, or null.
    /// @return Where its records are read, when it describes a pipeline in place whose translation a pre-processor
    /// feeds; empty otherwise (its raw streams are read as they are).
    Optional<At> forBuild(final String build) {
        if (NullSafe.isBlankString(build)) {
            return Optional.empty();
        }
        try {
            return buildRecords.get().startFrom(build).flatMap(TranslationInputs::at);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("How build {} starts wasn't read: {}", build, e.getMessage()), e);
            return Optional.empty();
        }
    }

    /// @param startFrom A build's start ([BuildRecords#startFrom]).
    /// @return Where its records are read, when it describes a pipeline in place whose translation a pre-processor
    /// feeds; else empty.
    static Optional<At> at(final Map<String, Object> startFrom) {
        if (!InPlaceInduction.IN_PLACE.equals(startFrom.get("choice"))) {
            return Optional.empty();
        }
        final Map<String, Object> inPlace = JsonValues.mapOrEmpty(startFrom.get(InPlaceInduction.IN_PLACE));
        final String pipeline = JsonValues.text(JsonValues.mapOrEmpty(startFrom.get("pipeline")).get("uuid"));
        final String element = JsonValues.text(inPlace.get(InPlaceInduction.ELEMENT));
        if (!Boolean.TRUE.equals(inPlace.get(InPlaceInduction.PRE_PROCESSED)) || pipeline.isEmpty()
            || element.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new At(pipeline, element));
    }

    /// Each stream's records at the translation's input, as one text a stream.
    ///
    /// @param context The call's context (its time limit).
    /// @param at      Where they are read.
    /// @param streams The raw streams.
    /// @param cap     The most records stepped a stream.
    /// @return Each stream's text, by stream, those that give none left out.
    Map<Long, TranslationInputText.Text> texts(final ToolCallContext context,
                                               final At at,
                                               final List<Long> streams,
                                               final int cap) {
        stepping.checkCanStep();
        final SteppedPipeline loaded = stepping.load(at.pipelineUuid());
        final Map<Long, TranslationInputText.Text> out = new LinkedHashMap<>();
        for (final Long stream : NullSafe.list(streams)) {
            if (stream == null) {
                continue;
            }
            final PipelineStepping.Outputs outputs = PipelineStepping.withinTimeLimit(context, () ->
                    stepping.outputs(context, loaded, List.of(stream), at.element(), null, cap));
            TranslationInputText.of(new ArrayList<>(outputs.inputs().values()))
                    .ifPresent(text -> out.put(stream, text));
            if (outputs.stopped()) {
                break;
            }
        }
        if (!out.isEmpty()) {
            stepping.audit(context, List.copyOf(out.keySet()), "Stepped stream(s) through " + loaded.name()
                                                              + " to read its translation's input");
        }
        return out;
    }


    // --------------------------------------------------------------------------------


    /// Where a build's records are read.
    ///
    /// @param pipelineUuid The pipeline described in place.
    /// @param element      Its translation's element, whose input is read.
    record At(String pipelineUuid, String element) {

    }
}
