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
import stroom.docref.DocRef;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.FetchProcessorRequest;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterExpressionUtil;
import stroom.processor.shared.ProcessorFilterRow;
import stroom.processor.shared.ProcessorListRow;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// The processor filters the ported tools read, as the user: what each filter's pipeline reads, by its feeds.
///
/// Stroom shows processor filters only to a user with the Manage Processors permission
/// ([ProcessorFilterService] refuses anyone else), and only for pipelines the user can view; so do these.
class ProcessorFilters {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorFilters.class);

    static final String FEED = "Feed";
    static final String TYPE = "Type";
    static final String PIPELINE = "Pipeline";
    static final String ID = "Id";
    static final String EVENTS = "Events";

    private final SecurityContext securityContext;
    private final Provider<ProcessorFilterService> processorFilterServiceProvider;
    private final DocumentAccess documentAccess;
    private final PipelineFeeds pipelineFeeds;

    /// @param securityContext                Checks the Manage Processors permission.
    /// @param processorFilterServiceProvider Finds the filters.
    /// @param documentAccess                 Checks View on each filter's pipeline.
    /// @param pipelineFeeds                  Reads the feeds a filter selects.
    @Inject
    ProcessorFilters(final SecurityContext securityContext,
                     final Provider<ProcessorFilterService> processorFilterServiceProvider,
                     final DocumentAccess documentAccess,
                     final PipelineFeeds pipelineFeeds) {
        this.securityContext = securityContext;
        this.processorFilterServiceProvider = processorFilterServiceProvider;
        this.documentAccess = documentAccess;
        this.pipelineFeeds = pipelineFeeds;
    }

    /// @return True if the user may manage processors, as reading processor filters needs. False if they may not,
    /// or if checking fails (failing closed).
    boolean canManageProcessors() {
        try {
            return securityContext.hasAppPermission(AppPermission.MANAGE_PROCESSORS_PERMISSION);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to check the Manage Processors permission", e);
            return false;
        }
    }

    /// @return Every processor filter that isn't deleted, of a pipeline the user can view. Empty if the user may
    /// not manage processors.
    List<ProcessorFilter> all() {
        if (!canManageProcessors()) {
            return List.of();
        }
        try {
            final ResultPage<ProcessorListRow> rows = processorFilterServiceProvider.get()
                    .find(new FetchProcessorRequest(ProcessorFilterExpressionUtil.createBasicExpression()));
            final List<ProcessorFilter> filters = new ArrayList<>();
            for (final ProcessorListRow row : NullSafe.list(NullSafe.get(rows, ResultPage::getValues))) {
                if (row instanceof final ProcessorFilterRow filterRow
                    && filterRow.getProcessorFilter() != null
                    && !filterRow.getProcessorFilter().isDeleted()
                    && filterRow.getProcessorFilter().getPipelineUuid() != null
                    && documentAccess.canView(filterRow.getProcessorFilter().getPipeline())) {
                    filters.add(filterRow.getProcessorFilter());
                }
            }
            return filters;
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to find the processor filters", e);
            return List.of();
        }
    }

    /// @param filters Processor filters.
    /// @return The feeds each pipeline's filters name in their top-level Feed terms (by value, or the feed
    /// document's name), by the pipeline's UUID.
    static Map<String, Set<String>> feedsByPipeline(final List<ProcessorFilter> filters) {
        final Map<String, Set<String>> feeds = new LinkedHashMap<>();
        for (final ProcessorFilter filter : filters) {
            final Set<String> names = feeds.computeIfAbsent(filter.getPipelineUuid(), k -> new LinkedHashSet<>());
            final ExpressionOperator expression = NullSafe.get(filter.getQueryData(), QueryData::getExpression);
            for (final ExpressionItem item : NullSafe.list(NullSafe.get(expression, ExpressionOperator::getChildren))) {
                if (item instanceof final ExpressionTerm term && FEED.equals(term.getField())) {
                    final String name = NullSafe.isNonEmptyString(term.getValue())
                            ? term.getValue()
                            : NullSafe.get(term.getDocRef(), DocRef::getName);
                    if (NullSafe.isNonEmptyString(name)) {
                        names.add(name);
                    }
                }
            }
        }
        return feeds;
    }

    /// @param filter A processor filter.
    /// @return Its expression's terms, at any depth.
    static List<ExpressionTerm> terms(final ProcessorFilter filter) {
        return terms(NullSafe.get(filter.getQueryData(), QueryData::getExpression));
    }

    /// @param expression An expression, or null.
    /// @return Its terms, at any depth.
    static List<ExpressionTerm> terms(final ExpressionOperator expression) {
        return PipelineFeeds.filterTerms(expression);
    }

    /// The feeds a processor filter reads, lower-cased: Feed EQUALS (one, or several between commas), IS_DOC_REF
    /// (the feed doc: its name is in the reference, with no value), IN_DICTIONARY (the feeds a Dictionary lists,
    /// and its imports'), or with no Feed term, the feed of the first stream its Id terms pick (the server's
    /// filters on sample streams). Seen on a production Stroom: 34 filters named their feed by IS_DOC_REF and 6 by
    /// a dictionary, which reading the value alone missed.
    ///
    /// @param terms A filter's terms.
    /// @param known Dictionaries and streams already read, across one walk of the filters (see [PipelineFeeds]).
    /// @return The feeds, lower-cased.
    Set<String> feeds(final List<ExpressionTerm> terms, final Map<Object, Object> known) {
        return pipelineFeeds.filterFeeds(terms, known, true);
    }

    /// Pipelines reading a pipeline's Events: their filters name it (Pipeline IS_DOC_REF) or its Events feed.
    ///
    /// @param pipelineUuid The pipeline.
    /// @param eventsFeeds  The feeds its Events are in.
    /// @return Each such pipeline as `{uuid, name, filters}`.
    List<Map<String, Object>> followOnPipelines(final String pipelineUuid, final Set<String> eventsFeeds) {
        final Set<String> feeds = new LinkedHashSet<>();
        eventsFeeds.forEach(feed -> feeds.add(feed.toLowerCase(Locale.ROOT)));
        final Map<String, Map<String, Object>> found = new LinkedHashMap<>();
        final Map<Object, Object> known = new LinkedHashMap<>();
        for (final ProcessorFilter filter : all()) {
            if (Objects.equals(filter.getPipelineUuid(), pipelineUuid)) {
                continue;
            }
            final List<ExpressionTerm> terms = terms(filter);
            final boolean namesIt = terms.stream()
                    .anyMatch(term -> PIPELINE.equals(term.getField())
                                      && Objects.equals(NullSafe.get(term.getDocRef(), DocRef::getUuid),
                            pipelineUuid));
            final boolean readsFeed = terms.stream()
                                              .anyMatch(term -> TYPE.equals(term.getField())
                                                                && EVENTS.equals(term.getValue()))
                                      && feeds(terms, known).stream().anyMatch(feeds::contains);
            if (namesIt || readsFeed) {
                @SuppressWarnings("unchecked") final List<Object> ids = (List<Object>) found
                        .computeIfAbsent(filter.getPipelineUuid(), uuid -> {
                            final Map<String, Object> entry = new LinkedHashMap<>();
                            entry.put("uuid", uuid);
                            entry.put("name", filter.getPipelineName());
                            entry.put("filters", new ArrayList<>());
                            return entry;
                        }).get("filters");
                ids.add(filter.getId());
            }
        }
        return List.copyOf(found.values());
    }
}
