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

import stroom.ai.impl.tools.instructions.FieldRuleChoices;
import stroom.ai.impl.tools.instructions.InstructionRules.FieldRule;
import stroom.docref.DocRef;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Map;

/// The `## Fields` lines the user chose for a build's own data sources (its index docs) where the AGENTS docs of
/// its feeds' folders conflict (compare_feeds, decided by the user 2026-11-25): the choice kept in the build's record
/// ([BuildRecords#agentsChoices]), for the tools that look fields up in the build's index (activity_timeline,
/// compare_activity) to use in place of the merged rules.
class BuildFieldRuleChoices implements FieldRuleChoices {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(BuildFieldRuleChoices.class);

    private final Provider<WriteGuard> guardProvider;
    private final Provider<BuildRecords> recordsProvider;

    /// @param guardProvider   Finds the build a document is in.
    /// @param recordsProvider Reads the build's record.
    @Inject
    BuildFieldRuleChoices(final Provider<WriteGuard> guardProvider, final Provider<BuildRecords> recordsProvider) {
        this.guardProvider = guardProvider;
        this.recordsProvider = recordsProvider;
    }

    @Override
    public Map<String, FieldRule> chosenFor(final DocRef dataSource) {
        try {
            return guardProvider.get().buildOf(dataSource)
                    .map(build -> AgentsConflicts.fieldsChosen(recordsProvider.get().agentsChoices(build)))
                    .orElse(Map.of());
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No field choices for {}: {}", dataSource, e.getMessage()));
            return Map.of();
        }
    }
}
