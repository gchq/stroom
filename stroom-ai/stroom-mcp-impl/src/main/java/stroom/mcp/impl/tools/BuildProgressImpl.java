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

import stroom.ai.impl.tools.BuildProgress;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.processing.AcceptedErrors;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;
import java.util.Optional;

/// What Stroom's own tools (`stroom-ai-impl`) learn of the session's build: from the build it last worked on
/// ([BuildPlan#currentBuild]) and that build's plan, and the errors a build's recorded choices accept.
class BuildProgressImpl implements BuildProgress {

    private final Provider<BuildPlan> planProvider;
    private final Provider<BuildRecords> recordsProvider;

    /// @param planProvider    Reads the session's build and its plan.
    /// @param recordsProvider Reads the build's record of the user's choices.
    @Inject
    BuildProgressImpl(final Provider<BuildPlan> planProvider, final Provider<BuildRecords> recordsProvider) {
        this.planProvider = planProvider;
        this.recordsProvider = recordsProvider;
    }

    @Override
    public Optional<String> beforeDashboard(final ToolCallContext context) {
        final BuildPlan plan = planProvider.get();
        return plan.currentBuild(context).flatMap(plan::unverifiedIndexing);
    }

    @Override
    public List<AcceptedErrors.Entry> acceptedErrors(final String pipelineUuid) {
        // The user chose to cut long values for the build: the JSON parser's Warning that it cut one.
        return planProvider.get().buildOf(new DocRef(PipelineDoc.TYPE, pipelineUuid))
                .flatMap(build -> LongValues.accepted(recordsProvider.get(), build))
                .map(List::of)
                .orElse(List.of());
    }

    @Override
    public List<AcceptedErrors.Entry> explainedErrors(final String pipelineUuid) {
        // The user chose to keep long values whole for the build, and the JSON parser still cut one.
        return planProvider.get().buildOf(new DocRef(PipelineDoc.TYPE, pipelineUuid))
                .flatMap(build -> LongValues.stillCut(recordsProvider.get(), build))
                .map(List::of)
                .orElse(List.of());
    }
}
