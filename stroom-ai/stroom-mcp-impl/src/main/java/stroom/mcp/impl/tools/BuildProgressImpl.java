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

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Optional;

/// What Stroom's own tools (`stroom-ai-impl`) learn of the session's build: from the build it last worked on
/// ([BuildPlan#currentBuild]) and that build's plan.
class BuildProgressImpl implements BuildProgress {

    private final Provider<BuildPlan> planProvider;

    /// @param planProvider Reads the session's build and its plan.
    @Inject
    BuildProgressImpl(final Provider<BuildPlan> planProvider) {
        this.planProvider = planProvider;
    }

    @Override
    public Optional<String> beforeDashboard(final ToolCallContext context) {
        final BuildPlan plan = planProvider.get();
        return plan.currentBuild(context).flatMap(plan::unverifiedIndexing);
    }
}
