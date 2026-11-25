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

import stroom.ai.impl.UserActions;
import stroom.ai.impl.tools.StroomLinks;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.DocDependencyService;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/// How a promotion's new filters take over feeds that filters outside the build already select (decided by the user,
/// 2026-11-25): a new version moves only the build's feeds; the earlier version keeps the rest. Where an old filter
/// (any pipeline's) selects any of a new filter's feeds, the new filter is made disabled and the switch-over is the
/// user's, as in-flight tasks make it unsafe for the tools: remove the feeds from the old filter (or from the
/// dictionary it selects them by, naming the dictionary's other users), let the old filter's in-flight tasks finish,
/// then enable the new filter, each step linked. The tools change no old filter or dictionary.
///
/// Relationships are read live from the filters' expressions ([PipelineFeeds]), never from Stroom's dependency
/// service, which records only a filter's `docRef` terms; only a dictionary's other documents come from it.
class PromotionSwitchOver {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PromotionSwitchOver.class);

    /// The most documents using a dictionary named.
    static final int MAX_DICTIONARY_USERS = 20;
    private static final Pattern WILDCARD = Pattern.compile("[*?]");

    private final Provider<ProcessorFilters> processorFiltersProvider;
    private final PipelineFeeds pipelineFeeds;
    private final Provider<DocDependencyService> dependenciesProvider;
    private final StreamProcessing streamProcessing;

    /// @param processorFiltersProvider Reads every processor filter the user can see.
    /// @param pipelineFeeds            Reads the feeds a filter selects, its dictionaries' lines among them.
    /// @param dependenciesProvider     Finds the documents using a dictionary.
    /// @param streamProcessing         Counts a filter's tasks still to finish.
    @Inject
    PromotionSwitchOver(final Provider<ProcessorFilters> processorFiltersProvider,
                        final PipelineFeeds pipelineFeeds,
                        final Provider<DocDependencyService> dependenciesProvider,
                        final StreamProcessing streamProcessing) {
        this.processorFiltersProvider = processorFiltersProvider;
        this.pipelineFeeds = pipelineFeeds;
        this.dependenciesProvider = dependenciesProvider;
        this.streamProcessing = streamProcessing;
    }

    /// The old filters selecting each planned filter's feeds.
    ///
    /// @param plan What [PromotionProcessing#promotionProcessing] planned.
    /// @param ours True for a pipeline of the build (by UUID): its filters are the build's own.
    /// @return For each entry of the plan, in its order, the old filters selecting any of its feeds (none for an
    /// entry reading another pipeline's Events, or a feed no old filter selects).
    List<List<OldSelection>> overlaps(final List<PromotionProcessing.Entry> plan, final Predicate<String> ours) {
        if (plan.stream().allMatch(entry -> entry.feeds().isEmpty())) {
            return plan.stream().map(entry -> List.<OldSelection>of()).toList();
        }
        final List<ProcessorFilter> all = processorFiltersProvider.get().all();
        final Map<Object, Object> known = new HashMap<>();
        final List<List<OldSelection>> out = new ArrayList<>();
        for (final PromotionProcessing.Entry entry : plan) {
            final List<OldSelection> found = new ArrayList<>();
            if (!entry.feeds().isEmpty()) {
                for (final ProcessorFilter filter : all) {
                    if (!ours.test(filter.getPipelineUuid())
                        && !Objects.equals(filter.getPipelineUuid(), entry.pipeline().getUuid())) {
                        selection(filter, entry.feeds(), entry.streamType(), all, known).ifPresent(found::add);
                    }
                }
            }
            out.add(List.copyOf(found));
        }
        return out;
    }

    /// How an old filter selects any of the feeds: which it selects (moving to the new filter), which others it
    /// selects (staying with it), and by what (its own terms, a dictionary, or a condition whose feeds can't be
    /// listed).
    ///
    /// @param filter     A processor filter outside the build.
    /// @param feeds      The new filter's feeds.
    /// @param streamType The new filter's stream type.
    /// @param all        Every filter, for a dictionary's other users.
    /// @param known      Dictionaries and streams already read.
    /// @return How it selects them; empty when it selects none of them, by stream id (a sample), or another pipeline's
    /// Events (a follow-on: see [PromotionProcessing#followOns]).
    Optional<OldSelection> selection(final ProcessorFilter filter,
                                     final List<String> feeds,
                                     final String streamType,
                                     final List<ProcessorFilter> all,
                                     final Map<Object, Object> known) {
        // A filter with no condition selects every stream.
        final ExpressionOperator expression = Objects.requireNonNullElseGet(
                ProcessorFilterWrites.expressionOf(filter), () -> ExpressionOperator.builder().build());
        if (!ProcessorFilterWrites.selectedIds(expression).isEmpty()
            || ProcessorFilterWrites.namesPipeline(expression)) {
            return Optional.empty();
        }
        final List<String> moving = feeds.stream()
                .filter(feed -> mayRead(expression, feed, streamType, known))
                .toList();
        if (moving.isEmpty()) {
            return Optional.empty();
        }
        final Optional<Set<String>> enumerated = pipelineFeeds.selectedFeeds(expression, known);
        final Set<String> movingLower = lowered(moving);
        final List<String> staying = enumerated.map(found -> found.stream()
                        .filter(feed -> !movingLower.contains(feed.toLowerCase(Locale.ROOT)))
                        .toList())
                .orElse(List.of());
        final List<ExpressionTerm> terms = PipelineFeeds.filterTerms(expression);
        final List<String> inTerms = new ArrayList<>();
        final List<DictionaryUse> dictionaries = new ArrayList<>();
        for (final String feed : moving) {
            boolean placed = false;
            for (final ExpressionTerm term : terms) {
                if (!MetaFields.FIELD_FEED.equals(term.getField())
                    || !pipelineFeeds.filterFeeds(List.of(term), known, true)
                        .contains(feed.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                placed = true;
                if (Condition.IN_DICTIONARY.equals(term.getCondition()) && term.getDocRef() != null) {
                    final DocRef dictionary = new DocRef(DictionaryDoc.TYPE, term.getDocRef().getUuid(),
                            term.getDocRef().getName());
                    dictionaryUse(dictionaries, dictionary, filter, all).moving().add(feed);
                } else if (!inTerms.contains(feed)) {
                    inTerms.add(feed);
                }
            }
            if (!placed && enumerated.isPresent() && !inTerms.contains(feed)) {
                inTerms.add(feed);
            }
        }
        final DocRef pipeline = new DocRef(PipelineDoc.TYPE, filter.getPipelineUuid(),
                NullSafe.nonBlankStringElse(filter.getPipelineName(), filter.getPipelineUuid()));
        return Optional.of(new OldSelection(filter.getId(), pipeline, running(filter), moving, staying, inTerms,
                List.copyOf(dictionaries), enumerated.isPresent()
                        ? null
                        : unlisted(expression, known)));
    }

    /// What the review says of the old filters selecting the new filters' feeds: per filter, the feeds that move and
    /// those that stay.
    ///
    /// @param plan     What [PromotionProcessing#promotionProcessing] planned.
    /// @param overlaps What [#overlaps] found, in the plan's order.
    /// @return One line per old filter and new filter.
    static List<String> said(final List<PromotionProcessing.Entry> plan, final List<List<OldSelection>> overlaps) {
        final List<String> out = new ArrayList<>();
        for (int i = 0; i < plan.size() && i < overlaps.size(); i++) {
            for (final OldSelection selection : overlaps.get(i)) {
                out.add(selection.said() + ": the new filter of '" + plan.get(i).pipeline().getName() + "' is made "
                        + "disabled, the switch-over the user's");
            }
        }
        return out;
    }

    /// The switch-over, for each new filter whose feeds old filters select: its steps (said in order, each linked)
    /// and its buttons, the new filter's enable button last, after the others. The new filter is disabled, as every
    /// filter promotion makes; nothing else is changed.
    ///
    /// @param links    How the caller links to Stroom.
    /// @param overlaps What [#overlaps] found, in the plan's order.
    /// @param made     What [PromotionProcessing#createPromotionFilters] made, in the plan's order: changed in place.
    void handOver(final StroomLinks links,
                  final List<List<OldSelection>> overlaps,
                  final List<Map<String, Object>> made) {
        for (int i = 0; i < made.size() && i < overlaps.size(); i++) {
            final Map<String, Object> result = made.get(i);
            final List<OldSelection> old = overlaps.get(i);
            if (old.isEmpty() || Boolean.TRUE.equals(result.get("existing"))) {
                continue;
            }
            final Object filterId = result.get("filter_id");
            final List<String> steps = new ArrayList<>();
            final List<Map<String, Object>> actions = new ArrayList<>();
            final List<String> after = new ArrayList<>();
            final Set<String> dictionariesSaid = new LinkedHashSet<>();
            for (final OldSelection selection : old) {
                steps.add(selection.change(links, dictionariesSaid));
                for (final Map<String, Object> action : selection.actions(links)) {
                    if (actions.stream().noneMatch(other -> Objects.equals(other.get("id"), action.get("id")))) {
                        actions.add(action);
                        after.add(String.valueOf(action.get("id")));
                    }
                }
            }
            for (final OldSelection selection : old) {
                final int open = openTasks(selection);
                steps.add("Let filter " + selection.filterId() + "'s in-flight tasks finish (" + open + (open
                        >= StreamProcessing.MAX_TASKS
                        ? " or more"
                        : "") + " open now for " + String.join(", ", selection.moving()) + "; processing_status "
                          + "shows them): " + links.document(selection.pipeline()));
                after.add("filter " + selection.filterId() + "'s in-flight tasks have finished");
            }
            steps.add("Enable the new filter " + filterId + " ('" + result.get("pipeline") + "'): "
                      + NullSafe.string(Objects.toString(result.get("pipeline_link"), "")));
            result.put("switch_over", steps);
            result.put("enabled", false);
            @SuppressWarnings("unchecked") final List<Map<String, Object>> existing =
                    (List<Map<String, Object>>) result.get(UserActionEntries.USER_ACTIONS);
            final List<Map<String, Object>> buttons = new ArrayList<>(actions);
            if (existing != null) {
                for (final Map<String, Object> action : existing) {
                    if (UserActions.ENABLE_PROCESSOR_FILTER.equals(action.get("action"))) {
                        @SuppressWarnings("unchecked") final List<String> before =
                                (List<String>) action.computeIfAbsent("after", key -> new ArrayList<String>());
                        final List<String> ordered = new ArrayList<>(before);
                        ordered.addAll(after);
                        action.put("after", ordered);
                    }
                    buttons.add(action);
                }
            }
            result.put(UserActionEntries.USER_ACTIONS, buttons);
        }
    }

    /// What an earlier version keeps once its new version takes the build's feeds: each of its filters that selects
    /// a feed outside the build, or feeds that can't be listed (never advised to be disabled or removed).
    ///
    /// @param predecessor The earlier version.
    /// @param moving      The build's feeds, which move.
    /// @return What each such filter keeps, e.g. `filter 3 (ACME-B)`; none when every filter of it selects only the
    /// build's feeds, or by stream id.
    List<String> keeps(final DocRef predecessor, final Set<String> moving) {
        final Set<String> movingLower = lowered(moving);
        final List<String> out = new ArrayList<>();
        final Map<Object, Object> known = new HashMap<>();
        for (final ProcessorFilter filter : processorFiltersProvider.get().all()) {
            if (!Objects.equals(filter.getPipelineUuid(), predecessor.getUuid())) {
                continue;
            }
            final ExpressionOperator expression = Objects.requireNonNullElseGet(
                    ProcessorFilterWrites.expressionOf(filter), () -> ExpressionOperator.builder().build());
            if (!ProcessorFilterWrites.selectedIds(expression).isEmpty()) {
                continue;
            }
            final Optional<Set<String>> feeds = pipelineFeeds.selectedFeeds(expression, known);
            if (feeds.isEmpty()) {
                out.add("filter " + filter.getId() + " (" + unlisted(expression, known) + ")");
                continue;
            }
            final List<String> staying = feeds.get().stream()
                    .filter(feed -> !movingLower.contains(feed.toLowerCase(Locale.ROOT)))
                    .toList();
            if (!staying.isEmpty()) {
                out.add("filter " + filter.getId() + " (" + String.join(", ", staying) + ")");
            }
        }
        return out;
    }

    /// Which of the feeds are selected twice (by filters of two or more pipelines, enabled or not, as both process it
    /// once enabled), which by no enabled filter (its new streams processed by none), and the tasks still open of the
    /// old filters selecting them: report only, for `build_status` and the promotion's result. Nothing is changed.
    ///
    /// @param feeds      The feeds a promotion moved.
    /// @param streamType Their stream type.
    /// @param newFilters The promotion's new filters, by id: their tasks aren't counted as old ones.
    /// @return One line per feed selected twice or by no enabled filter, and per old filter with tasks open; none
    /// when each feed is selected by one pipeline's enabled filter.
    List<String> selectionReport(final List<String> feeds, final String streamType, final Set<Integer> newFilters) {
        final List<ProcessorFilter> all = processorFiltersProvider.get().all();
        final Map<Object, Object> known = new HashMap<>();
        final List<String> out = new ArrayList<>();
        for (final String feed : feeds) {
            final List<ProcessorFilter> selecting = all.stream()
                    .filter(filter -> {
                        final ExpressionOperator expression = Objects.requireNonNullElseGet(
                                ProcessorFilterWrites.expressionOf(filter), () -> ExpressionOperator.builder().build());
                        return ProcessorFilterWrites.selectedIds(expression).isEmpty()
                               && !ProcessorFilterWrites.namesPipeline(expression)
                               && mayRead(expression, feed, streamType, known);
                    })
                    .toList();
            final List<ProcessorFilter> running = selecting.stream().filter(PromotionSwitchOver::running).toList();
            final Set<String> pipelines = new LinkedHashSet<>();
            selecting.forEach(filter -> pipelines.add(filter.getPipelineUuid()));
            if (pipelines.size() > 1) {
                out.add(feed + " (" + streamType + ") is selected twice, by filters of " + pipelines.size()
                        + " pipelines: " + named(selecting) + ". Once both are enabled, each of its streams is "
                        + "processed by each: until the old selection is changed (the switch-over in the "
                        + "promotion's next steps), leave the new filter disabled");
            }
            if (running.isEmpty()) {
                out.add(feed + " (" + streamType + ") is selected by no enabled filter, so its new streams aren't "
                        + "processed" + (selecting.isEmpty()
                        ? ""
                        : " (" + named(selecting) + ")"));
            }
            for (final ProcessorFilter filter : running) {
                if (newFilters.contains(filter.getId())) {
                    continue;
                }
                final int open = streamProcessing.openTasks(filter.getId(), List.of(feed));
                if (open > 0) {
                    out.add("filter " + filter.getId() + " ('" + NullSafe.nonBlankStringElse(filter.getPipelineName(),
                            filter.getPipelineUuid()) + "') still has " + open + (open >= StreamProcessing.MAX_TASKS
                            ? " or more"
                            : "") + " task(s) open for " + feed);
                }
            }
        }
        return out;
    }

    // The tasks still open of an old filter for the feeds that move; 0 when they can't be read.
    private int openTasks(final OldSelection selection) {
        try {
            return streamProcessing.openTasks(selection.filterId(), selection.moving());
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Tasks of filter {} not read: {}", selection.filterId(),
                    e.getMessage()));
            return 0;
        }
    }

    // The dictionary's use by the filter, added if not there yet, with the dictionary's other users.
    private DictionaryUse dictionaryUse(final List<DictionaryUse> uses,
                                        final DocRef dictionary,
                                        final ProcessorFilter filter,
                                        final List<ProcessorFilter> all) {
        for (final DictionaryUse use : uses) {
            if (use.dictionary().getUuid().equals(dictionary.getUuid())) {
                return use;
            }
        }
        final DictionaryUse use = new DictionaryUse(dictionary, new ArrayList<>(), otherUsers(dictionary, filter,
                all));
        uses.add(use);
        return use;
    }

    // What else uses a dictionary, found live: other filters naming it, and the documents Stroom records as using it
    // (an XSLT, another dictionary importing it).
    private List<String> otherUsers(final DocRef dictionary, final ProcessorFilter filter,
                                    final List<ProcessorFilter> all) {
        final Set<String> out = new LinkedHashSet<>();
        for (final ProcessorFilter other : all) {
            if (Objects.equals(other.getId(), filter.getId())) {
                continue;
            }
            final boolean names = PipelineFeeds.filterTerms(ProcessorFilterWrites.expressionOf(other)).stream()
                    .anyMatch(term -> Condition.IN_DICTIONARY.equals(term.getCondition())
                                      && dictionary.getUuid().equals(NullSafe.get(term.getDocRef(),
                            DocRef::getUuid)));
            if (names) {
                out.add("filter " + other.getId() + " of pipeline '" + NullSafe.nonBlankStringElse(
                        other.getPipelineName(), other.getPipelineUuid()) + "'");
            }
        }
        try {
            final DependencyCriteria criteria = new DependencyCriteria();
            criteria.setPageRequest(new PageRequest(0, MAX_DICTIONARY_USERS));
            criteria.setPartialName(DependencyCriteria.FIELD_DEF_TO_UUID.getFilterQualifier() + ":="
                                    + dictionary.getUuid());
            final ResultPage<Dependency> page = dependenciesProvider.get().fetchDependencies(criteria, Set.of());
            for (final Dependency dependency : NullSafe.list(NullSafe.get(page, ResultPage::getValues))) {
                final DocRef from = dependency.getFrom();
                if (from == null || dependency.getTo() == null
                    || !dictionary.getUuid().equals(dependency.getTo().getUuid())
                    || ProcessorFilter.ENTITY_TYPE.equals(from.getType())) {
                    // A processor filter is named by its id, above.
                    continue;
                }
                out.add(from.getType() + " '" + NullSafe.nonBlankStringElse(from.getName(), from.getUuid()) + "'");
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("The users of dictionary {} not read: {}", dictionary,
                    e.getMessage()));
        }
        return out.stream().limit(MAX_DICTIONARY_USERS).toList();
    }

    /// Whether a filter's expression may select a stream of the feed and type: exactly for the feed conditions the
    /// tools read (by reference, by name or several names, a wildcard, a dictionary's lines) and a stream type;
    /// anything else (a NOT, an unreadable dictionary, another field) may.
    ///
    /// @param item       A filter's expression, or a part of it.
    /// @param feed       A feed.
    /// @param streamType A stream type.
    /// @param known      Dictionaries and streams already read.
    /// @return True if it selects, or may select, the feed's streams of the type.
    boolean mayRead(final ExpressionItem item,
                    final String feed,
                    final String streamType,
                    final Map<Object, Object> known) {
        if (item instanceof final ExpressionOperator operator) {
            final List<Boolean> results = NullSafe.list(operator.getChildren()).stream()
                    .filter(ExpressionItem::enabled)
                    .map(child -> mayRead(child, feed, streamType, known))
                    .toList();
            final Op op = Objects.requireNonNullElse(operator.op(), Op.AND);
            if (op == Op.NOT || results.isEmpty()) {
                return true;
            }
            return op == Op.OR
                    ? results.contains(true)
                    : !results.contains(false);
        }
        if (!(item instanceof final ExpressionTerm term)) {
            return true;
        }
        final Condition condition = term.getCondition();
        if (MetaFields.FIELD_TYPE.equals(term.getField())
            && (condition == null || condition == Condition.EQUALS)) {
            return NullSafe.string(term.getValue()).strip().equalsIgnoreCase(streamType);
        }
        if (!MetaFields.FIELD_FEED.equals(term.getField())) {
            return true;
        }
        final String wanted = feed.toLowerCase(Locale.ROOT);
        if (condition == Condition.IN_DICTIONARY) {
            final Set<String> lines = pipelineFeeds.filterFeeds(List.of(term), known, true);
            // An unreadable dictionary may list it.
            return lines.isEmpty() || lines.stream().anyMatch(line -> matches(line, wanted));
        }
        if (condition == null || PipelineFeeds.POSITIVE.contains(condition)) {
            return pipelineFeeds.filterFeeds(List.of(term), known, true).stream()
                    .anyMatch(value -> matches(value, wanted));
        }
        if (condition == Condition.NOT_EQUALS) {
            return !matches(NullSafe.string(term.getValue()).strip().toLowerCase(Locale.ROOT), wanted);
        }
        return true;
    }

    // What makes a filter's feeds unlistable, in words.
    private String unlisted(final ExpressionOperator expression, final Map<Object, Object> known) {
        final List<ExpressionTerm> terms = PipelineFeeds.filterTerms(expression);
        final List<ExpressionTerm> feedTerms = terms.stream()
                .filter(term -> MetaFields.FIELD_FEED.equals(term.getField()))
                .toList();
        for (final ExpressionTerm term : feedTerms) {
            if (Condition.IN_DICTIONARY.equals(term.getCondition())
                && pipelineFeeds.filterFeeds(List.of(term), known, true).isEmpty()) {
                return "it selects through dictionary '" + NullSafe.nonBlankStringElse(NullSafe.get(
                        term.getDocRef(), DocRef::getName), "?") + "', which can't be read";
            }
            if (WILDCARD.matcher(NullSafe.string(term.getValue())).find()) {
                return "it selects by the wildcard '" + term.getValue() + "'";
            }
        }
        if (terms.isEmpty()) {
            return "it has no condition, so it selects every feed's streams";
        }
        if (feedTerms.isEmpty()) {
            return "it selects by stream type only, any feed's";
        }
        return "it selects by a condition (a NOT, or another than a feed's name) whose feeds can't be listed";
    }

    // A filter that runs: enabled, and its processor too (a processor not given counts as enabled).
    private static boolean running(final ProcessorFilter filter) {
        return filter.isEnabled() && Optional.ofNullable(filter.getProcessor()).map(Processor::isEnabled)
                .orElse(true);
    }

    // Filters by id and pipeline, e.g. `filter 12 ('ACME-V1.0-Events'), filter 40 ('ACME-V1.1-Events', disabled)`.
    private static String named(final List<ProcessorFilter> filters) {
        return String.join(", ", filters.stream()
                .map(filter -> "filter " + filter.getId() + " ('" + NullSafe.nonBlankStringElse(
                        filter.getPipelineName(), filter.getPipelineUuid()) + "'" + (running(filter)
                        ? ""
                        : ", disabled") + ")")
                .toList());
    }

    // A feed condition's value (lower-cased, a wildcard allowed) matching a feed (lower-cased).
    private static boolean matches(final String value, final String feed) {
        if (!WILDCARD.matcher(value).find()) {
            return value.equals(feed);
        }
        final StringBuilder regex = new StringBuilder();
        for (final char c : value.toCharArray()) {
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                default -> regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return feed.matches(regex.toString());
    }

    private static Set<String> lowered(final Collection<String> feeds) {
        final Set<String> out = new LinkedHashSet<>();
        feeds.forEach(feed -> out.add(feed.toLowerCase(Locale.ROOT)));
        return out;
    }


    // --------------------------------------------------------------------------------


    /// An old filter selecting some of a new filter's feeds.
    ///
    /// @param filterId     The old filter.
    /// @param pipeline     Its pipeline.
    /// @param running      True if it is enabled (its processor too).
    /// @param moving       The new filter's feeds it selects (or may: see `unlisted`), which move.
    /// @param staying      The other feeds it selects, which stay with it.
    /// @param inTerms      The moving feeds its own terms name.
    /// @param dictionaries The dictionaries it selects moving feeds by.
    /// @param unlisted     Why its feeds can't be listed, or null when they can.
    record OldSelection(int filterId,
                        DocRef pipeline,
                        boolean running,
                        List<String> moving,
                        List<String> staying,
                        List<String> inTerms,
                        List<DictionaryUse> dictionaries,
                        String unlisted) {

        /// @param filterId     The old filter.
        /// @param pipeline     Its pipeline.
        /// @param running      True if it runs.
        /// @param moving       The feeds that move.
        /// @param staying      The feeds that stay.
        /// @param inTerms      The moving feeds its own terms name.
        /// @param dictionaries The dictionaries it selects moving feeds by.
        /// @param unlisted     Why its feeds can't be listed, or null.
        OldSelection {
            moving = List.copyOf(moving);
            staying = List.copyOf(staying);
            inTerms = List.copyOf(inTerms);
            dictionaries = List.copyOf(dictionaries);
        }

        /// @return What the review says of it: the feeds that move, those that stay.
        String said() {
            final StringBuilder sb = new StringBuilder("filter " + filterId + " of pipeline '" + pipeline.getName()
                                                       + "'" + (running
                    ? ""
                    : " (disabled)") + " selects " + String.join(", ", moving));
            for (final DictionaryUse use : dictionaries) {
                sb.append(" (").append(String.join(", ", use.moving())).append(" through dictionary '")
                        .append(use.dictionary().getName()).append("')");
            }
            sb.append(moving.size() == 1
                    ? ", which moves"
                    : ", which move");
            if (unlisted != null) {
                sb.append("; ").append(unlisted).append(", so which other feeds it selects can't be listed: they "
                                                        + "stay with it");
            } else if (staying.isEmpty()) {
                sb.append("; it selects no other feed");
            } else {
                sb.append("; ").append(String.join(", ", staying)).append(staying.size() == 1
                        ? " stays with it"
                        : " stay with it");
            }
            return sb.toString();
        }

        /// @param links            How the caller links to Stroom.
        /// @param dictionariesSaid The dictionaries already said in this switch-over, by UUID: added to.
        /// @return The step changing it, linked: remove the moving feeds from it, or from the dictionary it selects
        /// them by (naming the dictionary's other users), disable it where it selects nothing else, or change a
        /// selection whose feeds can't be listed.
        String change(final StroomLinks links, final Set<String> dictionariesSaid) {
            final List<String> parts = new ArrayList<>();
            final String of = "filter " + filterId + " of pipeline " + links.document(pipeline) + " (its Processors "
                              + "tab)";
            if (unlisted != null) {
                parts.add("Change " + of + " so it no longer selects " + String.join(", ", moving) + ": "
                          + unlisted + ", so its feeds can't be listed");
            } else if (staying.isEmpty() && dictionaries.isEmpty()) {
                parts.add("Disable " + of + ": it selects only " + String.join(", ", moving) + ", which the new "
                          + "filter takes over");
            } else if (!inTerms.isEmpty()) {
                parts.add("Remove " + String.join(", ", inTerms) + " from " + of + (staying.isEmpty()
                        ? ""
                        : "; it goes on selecting " + String.join(", ", staying)));
            }
            for (final DictionaryUse use : dictionaries) {
                if (!dictionariesSaid.add(use.dictionary().getUuid())) {
                    parts.add("Filter " + filterId + " of pipeline '" + pipeline.getName() + "' selects "
                              + String.join(", ", use.moving()) + " by the same dictionary '"
                              + use.dictionary().getName() + "': changed with it");
                    continue;
                }
                parts.add("Remove " + String.join(", ", use.moving()) + " from dictionary "
                          + links.document(use.dictionary()) + ", which " + "filter " + filterId + " of pipeline '"
                          + pipeline.getName() + "' selects them by; " + (use.users().isEmpty()
                        ? "nothing else uses it"
                        : "it is also used by " + String.join(", ", use.users()) + ", which stop selecting them "
                          + "too"));
            }
            return String.join(". ", parts);
        }

        /// @param links How the caller links to Stroom.
        /// @return Its buttons: open the pipeline (its filter) or the dictionary to change.
        List<Map<String, Object>> actions(final StroomLinks links) {
            final List<Map<String, Object>> out = new ArrayList<>();
            final String what;
            if (unlisted != null) {
                what = "change filter " + filterId + " so it no longer selects " + String.join(", ", moving);
            } else if (staying.isEmpty() && dictionaries.isEmpty()) {
                what = "disable filter " + filterId + ", which selects only " + String.join(", ", moving);
            } else if (!inTerms.isEmpty()) {
                what = "remove " + String.join(", ", inTerms) + " from filter " + filterId;
            } else {
                what = null;
            }
            if (what != null) {
                final Map<String, Object> open = UserActionEntries.openDocument(links, pipeline, what);
                open.put("id", "switch-" + filterId);
                out.add(open);
            }
            for (final DictionaryUse use : dictionaries) {
                final Map<String, Object> open = UserActionEntries.openDocument(links, use.dictionary(),
                        "remove " + String.join(", ", use.moving()) + " (filter " + filterId + " selects by it)");
                final String uuid = use.dictionary().getUuid();
                open.put("id", "switch-dict-" + uuid.substring(0, Math.min(8, uuid.length())));
                out.add(open);
            }
            return out;
        }
    }

    /// A dictionary an old filter selects moving feeds by.
    ///
    /// @param dictionary The dictionary.
    /// @param moving     The moving feeds it lists.
    /// @param users      What else uses it, found live.
    record DictionaryUse(DocRef dictionary, List<String> moving, List<String> users) {

    }
}
