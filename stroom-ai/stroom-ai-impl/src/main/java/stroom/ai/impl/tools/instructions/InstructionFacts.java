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

package stroom.ai.impl.tools.instructions;

import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.instructions.AgentInstructions.InstructionDoc;
import stroom.ai.impl.tools.instructions.IndexFieldNames.IndexFields;
import stroom.ai.impl.tools.instructions.InstructionChecks.Facts;
import stroom.ai.impl.tools.instructions.InstructionChecks.Finding;
import stroom.ai.impl.tools.instructions.InstructionChecks.IndexFacts;
import stroom.ai.impl.tools.instructions.InstructionChecks.PlanField;
import stroom.ai.impl.tools.query.DataSourceDocumentation;
import stroom.ai.impl.tools.query.DataSourceDocumentation.Planned;
import stroom.ai.impl.tools.timeline.TimelineFields;
import stroom.docref.DocRef;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Gathers what [InstructionChecks] needs of Stroom for the AGENTS docs of a folder: the AGENTS docs the user can
/// view, and the indexes the user can use where they apply, each with its fields, the fields the index plans kept
/// with the pipelines feeding it map the `## Fields` keys' event-logging paths to, and the user field a field
/// convention gives.
public class InstructionFacts {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(InstructionFacts.class);

    /// The most indexes whose plans are read: a check of a doc at the top of the tree reads every index's fields,
    /// and a plan needs the pipelines feeding the index.
    static final int MAX_PLANNED = 50;

    private final AgentInstructions agentInstructions;
    private final IndexFieldNames indexFieldNames;
    private final Provider<DataSourceDocumentation> documentationProvider;
    private final Provider<TimelineFields> timelineFieldsProvider;

    /// @param agentInstructions      The AGENTS docs.
    /// @param indexFieldNames        The indexes and their fields.
    /// @param documentationProvider  The index plans kept with the pipelines feeding an index.
    /// @param timelineFieldsProvider The field conventions.
    @Inject
    public InstructionFacts(final AgentInstructions agentInstructions,
                            final IndexFieldNames indexFieldNames,
                            final Provider<DataSourceDocumentation> documentationProvider,
                            final Provider<TimelineFields> timelineFieldsProvider) {
        this.agentInstructions = agentInstructions;
        this.indexFieldNames = indexFieldNames;
        this.documentationProvider = documentationProvider;
        this.timelineFieldsProvider = timelineFieldsProvider;
    }

    /// @param session The session, for the AGENTS docs' audit.
    /// @param purpose Why the docs are read, for the audit log.
    /// @return The AGENTS docs the user can view, most general first.
    public List<InstructionDoc> docs(final ToolSession session, final String purpose) {
        return agentInstructions.load(session, purpose).docs();
    }

    /// @param folderRef A folder, or the System root.
    /// @return Its path, e.g. `/Feeds/Acme`, or `/`.
    public String folderPath(final DocRef folderRef) {
        return agentInstructions.folderPath(folderRef);
    }

    /// @param docs   The AGENTS docs to check against, most general first.
    /// @param folder The folder whose indexes are gathered (and those below it), e.g. `/`.
    /// @return The facts; without indexes where they can't be read.
    public Facts gather(final List<InstructionDoc> docs, final String folder) {
        final List<IndexFacts> indexes = new ArrayList<>();
        final List<IndexFields> found;
        try {
            found = indexFieldNames.indexes(folder);
        } catch (final RuntimeException e) {
            LOGGER.debug("Unable to read the indexes under {}", folder, e);
            return new Facts(docs, List.of());
        }
        for (final IndexFields index : found) {
            final Map<String, PlanField> planned = indexes.size() < MAX_PLANNED
                    ? planned(index)
                    : Map.of();
            indexes.add(new IndexFacts(index.dataSource().getName(), index.folder(), index.fields(), planned,
                    conventions(index)));
        }
        return new Facts(docs, indexes);
    }

    /// Checks a draft of the AGENTS doc in a folder ([InstructionChecks#check]) against the other docs and the
    /// indexes in the folder and below.
    ///
    /// @param session The session, for the AGENTS docs' audit.
    /// @param purpose Why the docs are read, for the audit log.
    /// @param folder  The folder's path, e.g. `/Feeds/Acme`.
    /// @param text    The draft.
    /// @return Its findings.
    public List<Finding> checkDraft(final ToolSession session,
                                    final String purpose,
                                    final String folder,
                                    final String text) {
        final String at = AgentInstructions.normalise(folder);
        final List<InstructionDoc> others = docs(session, purpose).stream()
                .filter(doc -> !doc.folder().equalsIgnoreCase(at))
                .toList();
        return InstructionChecks.check(at, text, gather(others, at), true);
    }

    // For each ## Fields key with an event-logging path, the field a feeding pipeline's plan maps it to.
    private Map<String, PlanField> planned(final IndexFields index) {
        final Map<String, PlanField> out = new LinkedHashMap<>();
        try {
            final DataSourceDocumentation documentation = documentationProvider.get();
            final List<DocRef> pipelines = documentation.feedingPipelines(index.dataSource());
            if (pipelines.isEmpty()) {
                return out;
            }
            InstructionRules.CONVENTION_PATHS.forEach((key, path) -> documentation.plannedFrom(pipelines, path)
                    .stream()
                    .filter(planned -> index.fields().contains(planned.field().name()))
                    .findFirst()
                    .map(InstructionFacts::planField)
                    .ifPresent(field -> out.put(key, field)));
        } catch (final RuntimeException e) {
            LOGGER.debug("Unable to read the index plans feeding {}", index.dataSource(), e);
        }
        return out;
    }

    private static PlanField planField(final Planned planned) {
        return new PlanField(planned.field().name(), planned.pipeline().getName());
    }

    // The user field a field convention gives, where the index has it.
    private Map<String, String> conventions(final IndexFields index) {
        try {
            return timelineFieldsProvider.get()
                    .conventions(InstructionRules.CONVENTION_PATHS.get(InstructionRules.USER_FIELD))
                    .values()
                    .stream()
                    .filter(index.fields()::contains)
                    .findFirst()
                    .map(field -> Map.of(InstructionRules.USER_FIELD, field))
                    .orElse(Map.of());
        } catch (final RuntimeException e) {
            LOGGER.debug("Unable to read the field conventions", e);
            return Map.of();
        }
    }
}
