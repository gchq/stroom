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

package stroom.ai.impl.tools;

import stroom.ai.impl.ExpressionDescriber;
import stroom.docref.DocRef;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.QueryData;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;
import java.util.Optional;

/// How the chat tools find and describe processor filters, as the user, as the Processors tab shows them.
///
/// A processor filter isn't a document, so it has no permissions of its own. Stroom shows one, and what it selects,
/// only to a user with the Manage Processors permission ([ProcessorFilterService] refuses anyone else), and the
/// Processors tab only for a pipeline (or analytic rule) the user can view. [#find(String)] needs both, and so do
/// the tools before they say what a filter selects. Every check fails closed.
public class ProcessorFilterAccess {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorFilterAccess.class);

    /// What a tool says in place of a filter's selection for a user without the Manage Processors permission.
    static final String NEEDS_MANAGE_PROCESSORS = "what it selects needs the Manage Processors permission, as the "
                                                  + "Processors tab does";

    private final SecurityContext securityContext;
    private final Provider<ProcessorFilterService> processorFilterServiceProvider;
    private final DocumentAccess documentAccess;

    /// @param securityContext                Checks the user's permissions.
    /// @param processorFilterServiceProvider Finds processor filters.
    /// @param documentAccess                 Checks View on a filter's pipeline.
    @Inject
    ProcessorFilterAccess(final SecurityContext securityContext,
                          final Provider<ProcessorFilterService> processorFilterServiceProvider,
                          final DocumentAccess documentAccess) {
        this.securityContext = securityContext;
        this.processorFilterServiceProvider = processorFilterServiceProvider;
        this.documentAccess = documentAccess;
    }

    /// @return True if the user may manage processors, as the Processors and Active Tasks tabs need. False if they
    /// may not, or if checking fails (failing closed).
    public boolean canManageProcessors() {
        try {
            return securityContext.hasAppPermission(AppPermission.MANAGE_PROCESSORS_PERMISSION);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to check the Manage Processors permission", e);
            return false;
        }
    }

    /// @param uuid A processor filter's UUID, as the model gave it.
    /// @return The filter, if the user may manage processors, it exists, and the user can view the pipeline (or
    /// analytic rule) it runs. Empty otherwise, with no difference between the cases.
    Optional<ProcessorFilter> find(final String uuid) {
        if (NullSafe.isBlankString(uuid) || !canManageProcessors()) {
            return Optional.empty();
        }
        try {
            return processorFilterServiceProvider.get().fetchByUuid(uuid.strip())
                    .filter(filter -> documentAccess.canView(filter.getPipeline()));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to find processor filter " + uuid, e);
            return Optional.empty();
        }
    }

    /// The processor filters of a pipeline (not deleted), as the Processors tab lists them. Asked of Stroom by
    /// pipeline: otherwise every filter on an instance (2,000 on a test stack) was read on each call.
    ///
    /// @param pipeline A pipeline (or analytic rule).
    /// @return Its filters, or why they can't be listed: the user needs the Manage Processors permission and View
    /// on the pipeline.
    public Filters filtersOf(final DocRef pipeline) {
        if (!canManageProcessors()) {
            return new Filters(List.of(), "You need the Manage Processors permission to list them, as the "
                                          + "Processors tab does");
        }
        if (pipeline == null || !documentAccess.canView(pipeline)) {
            return new Filters(List.of(), "No pipeline was found with that UUID, or you don't have permission to "
                                          + "view it");
        }
        try {
            final List<ProcessorFilter> filters = NullSafe.list(NullSafe.get(
                            processorFilterServiceProvider.get().find(pipeline), ResultPage::getValues)).stream()
                    .filter(filter -> pipeline.getUuid().equals(filter.getPipelineUuid()) && !filter.isDeleted())
                    .toList();
            return new Filters(filters, null);
        } catch (final RuntimeException e) {
            LOGGER.debug("Unable to list the processor filters of {}", pipeline, e);
            return new Filters(List.of(), NullSafe.nonBlankStringElse(e.getMessage(),
                    e.getClass().getSimpleName()));
        }
    }

    /// @param filter A processor filter.
    /// @return The pipeline (or analytic rule) it runs, with its name as the filter has it.
    static DocRef pipelineOf(final ProcessorFilter filter) {
        final DocRef pipeline = filter.getPipeline();
        return pipeline.copy()
                .name(NullSafe.nonBlankStringElse(filter.getPipelineName(), pipeline.getName()))
                .build();
    }

    /// Only for a filter the user may see the selection of: they have the Manage Processors permission and can view
    /// its pipeline.
    ///
    /// @param filter A processor filter.
    /// @return What it selects in words, e.g. `streams where Feed is 'ACME' and Type is 'Raw Events', created on or
    /// after 2026-01-01T00:00:00.000Z`, or `all streams` if its expression selects everything.
    static String selection(final ProcessorFilter filter) {
        final String expression = ExpressionDescriber.describe(
                NullSafe.get(filter.getQueryData(), QueryData::getExpression));
        final StringBuilder sb = new StringBuilder(expression == null
                ? "all streams"
                : "streams where " + expression);
        NullSafe.consume(filter.getMinMetaCreateTimeMs(), ms -> sb.append(", created on or after ")
                .append(DateUtil.createNormalDateTimeString(ms)));
        NullSafe.consume(filter.getMaxMetaCreateTimeMs(), ms -> sb.append(", created on or before ")
                .append(DateUtil.createNormalDateTimeString(ms)));
        return sb.toString();
    }

    /// @param filter A processor filter.
    /// @return Its state as the Processors tab shows it, e.g. `enabled, priority 10, Complete, 3 stream(s), 120
    /// event(s), last polled 2026-01-01T00:00:00.000Z`.
    static String status(final ProcessorFilter filter) {
        final StringBuilder sb = new StringBuilder(filter.isEnabled()
                ? "enabled"
                : "disabled");
        sb.append(", priority ").append(filter.getPriority());
        if (filter.isReprocess()) {
            sb.append(", reprocessing");
        }
        final ProcessorFilterTracker tracker = filter.getProcessorFilterTracker();
        if (tracker != null) {
            if (tracker.getStatus() != null) {
                sb.append(", ").append(tracker.getStatus().getDisplayValue());
            }
            if (tracker.getMetaCount() != null) {
                sb.append(", ").append(tracker.getMetaCount()).append(" stream(s)");
            }
            if (tracker.getEventCount() != null) {
                sb.append(", ").append(tracker.getEventCount()).append(" event(s)");
            }
            if (tracker.getLastPollMs() != null) {
                sb.append(", last polled ").append(DateUtil.createNormalDateTimeString(tracker.getLastPollMs()));
            }
            if (NullSafe.isNonBlankString(tracker.getMessage())) {
                sb.append(", message: ").append(tracker.getMessage().strip());
            }
        }
        if (filter.getRunAsUser() != null) {
            sb.append(", runs as ").append(filter.getRunAsUser().toDisplayString());
        }
        return sb.toString();
    }


    // --------------------------------------------------------------------------------


    /// A pipeline's processor filters, or why they couldn't be listed.
    ///
    /// @param filters The filters, in the order Stroom gives them; empty if they couldn't be listed.
    /// @param refused Why they couldn't be listed, or null if they were.
    public record Filters(List<ProcessorFilter> filters, String refused) {

    }
}
