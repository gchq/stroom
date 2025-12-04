/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.NodeFlag;
import stroom.query.api.DateTimeSettings;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.FieldProviderImpl;
import stroom.query.common.v2.SimpleStringExpressionParser.FieldProvider;
import stroom.query.common.v2.ValueFunctionFactoriesImpl;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.PredicateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.StringUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Some docRefs will get tested multiple times over the course of building the output tree
 * so hold a transient cache of filter matches. Also creates a combined predicate that only
 * includes the required checks bases on what is set in the ExplorerTreeFilter.
 */
class NodeInclusionChecker {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(NodeInclusionChecker.class);

    private static final Predicate<FilterableNode> ALWAYS_FALSE_PREDICATE = node -> false;
    private static final Predicate<FilterableNode> ALWAYS_TRUE_PREDICATE = node -> true;

    // TODO: 19/09/2023 FilterFieldMappers ought to support List/Sets of things so it does the test
    //  on each item in the list/set. This may be ok for now.
    private static final ValueFunctionFactoriesImpl<ExplorerNode> VALUE_FUNCTION_FACTORIES =
            new ValueFunctionFactoriesImpl<ExplorerNode>()
                    .put(ExplorerTreeFilter.FIELD_DEF_NAME, ExplorerNode::getName)
                    .put(ExplorerTreeFilter.FIELD_DEF_TYPE, ExplorerNode::getType)
                    .put(ExplorerTreeFilter.FIELD_DEF_UUID, ExplorerNode::getUuid)
                    .put(ExplorerTreeFilter.FIELD_DEF_TAG, NodeInclusionChecker::nodeToTagsString);

    private static final FieldProvider FIELD_PROVIDER = new FieldProviderImpl(ExplorerTreeFilter.FIELD_DEFINITIONS);

    private final SecurityContext securityContext;
    private final ExplorerTreeFilter filter;
    // Cache the fuzzy match filter outcomes by docref, as we get repeated tests because of the favourites.
    private final Map<DocRef, FilterOutcome> filterOutcomeMap = new HashMap<>();
    // Cache the outcome of perm checks as the favourites mean we have repeated tests for same docref
    private final Map<DocRef, Boolean> permCheckOutcomeMap = new HashMap<>();
    private final boolean hasNameFilter;
    private final ExpressionPredicateFactory expressionPredicateFactory;

    private final Predicate<FilterableNode> combinedPredicate;
    private Predicate<ExplorerNode> fuzzyMatchPredicate = null;


    NodeInclusionChecker(final SecurityContext securityContext,
                         final ExplorerTreeFilter filter,
                         final ExpressionPredicateFactory expressionPredicateFactory) {
        this.securityContext = securityContext;
        this.expressionPredicateFactory = expressionPredicateFactory;

        this.filter = Objects.requireNonNull(filter);
        this.hasNameFilter = !NullSafe.isBlankString(filter.getNameFilter());
        // We can build a single predicate based on what is in the ExplorerTreeFilter,
        // e.g. not doing a type check if no types set is provided.
        // The predicate is called for potentially every node, so it needs to be efficient.
        // For typical tree use, only the perm check will be active.
        this.combinedPredicate = buildCombinedPredicate(filter);
    }

    private static String nodeToTagsString(final ExplorerNode explorerNode) {
        // Don't use NodeTagSerialiser as that sorts, and we don't care about that and
        // don't want the perf hit.
        return explorerNode.getTags() == null
                ? null
                : String.join(ExplorerNode.TAGS_DELIMITER, explorerNode.getTags());
    }

    private Predicate<FilterableNode> buildCombinedPredicate(final ExplorerTreeFilter filter) {
        final Predicate<FilterableNode> combinedPredicate;
        final List<Predicate<FilterableNode>> predicates = new ArrayList<>(4);

        boolean foundAlwaysFalsePredicate = false;
        final Set<String> includedTypes = filter.getIncludedTypes();

        if (includedTypes != null) {
            if (includedTypes.isEmpty()) {
                LOGGER.debug("Always false triggered by checkType");
                foundAlwaysFalsePredicate = true;
            } else {
                LOGGER.debug("Adding checkType predicate for types: {}", includedTypes);
                predicates.add(this::checkType);
            }
        }

        final Set<String> filterTags = NullSafe.set(filter.getTags());
        if (!foundAlwaysFalsePredicate && !filterTags.isEmpty()) {
            LOGGER.debug("Adding checkTags predicate for tags: {}", filterTags);
            predicates.add(filterableNode ->
                    filterableNode.node.hasTags(filterTags));
        }

        final Set<NodeFlag> filterNodeFlags = NullSafe.set(filter.getNodeFlags());
        if (!foundAlwaysFalsePredicate && !filterNodeFlags.isEmpty()) {
            LOGGER.debug("Adding checkNodeFlags predicate for nodeFlags: {}", filterNodeFlags);
            predicates.add(filterableNode ->
                    filterableNode.node().hasNodeFlags(filterNodeFlags));
        }

        if (!foundAlwaysFalsePredicate) {
            final String nameFilter = filter.getNameFilter();
            if (!NullSafe.isBlankString(nameFilter)) {
                LOGGER.debug("Adding testWithNameFilter predicate for nameFilter: '{}'", nameFilter);
                // Ensure the predicate is initialised
                getFuzzyMatchPredicate();
                predicates.add(this::testWithNameFilter);
            }
        }

        if (!foundAlwaysFalsePredicate) {
            final Set<DocumentPermission> requiredPermissions = filter.getRequiredPermissions();
            if (NullSafe.isEmptyCollection(requiredPermissions)) {
                LOGGER.debug("Always false triggered by hasPermission");
                foundAlwaysFalsePredicate = true;
            } else {
                LOGGER.debug("Adding hasPermission predicate for requiredPermissions: {}", requiredPermissions);
                predicates.add(this::hasPermission);
            }
        }

        if (foundAlwaysFalsePredicate) {
            LOGGER.debug("Using always false predicate");
            combinedPredicate = ALWAYS_FALSE_PREDICATE;
        } else {
            LOGGER.debug(() -> LogUtil.message("Using combined predicate containing {} predicate{}",
                    predicates.size(), StringUtil.pluralSuffix(predicates.size())));
            combinedPredicate = PredicateUtil.andPredicates(predicates, ALWAYS_FALSE_PREDICATE);
        }
        return combinedPredicate;
    }

    private boolean testWithNameFilter(final FilterableNode filterableNode) {
        return filterableNode.ignoreNameFilter
               || doFuzzyMatchTest(filterableNode.node);
    }

    private boolean doFuzzyMatchTest(final ExplorerNode explorerNode) {
        return filterOutcomeMap.computeIfAbsent(explorerNode.getDocRef(), docRef2 ->
                FilterOutcome.fromPredicateResult(fuzzyMatchPredicate.test(explorerNode)))
                .isMatch;
    }

    boolean isFuzzyFilterMatch(final ExplorerNode node) {
        // We may have a nameFilter but not a fuzzyMatchPredicate if the user has selected no types
        // in the type filter
        return !hasNameFilter || fuzzyMatchPredicate == null
               || filterOutcomeMap.computeIfAbsent(node.getDocRef(), docRef ->
                FilterOutcome.fromPredicateResult(fuzzyMatchPredicate.test(node)))
                       .isMatch;
    }

    Predicate<ExplorerNode> getFuzzyMatchPredicate() {
        if (fuzzyMatchPredicate == null) {
            // Create the predicate for the current filter value, lazily as there may not be one
            fuzzyMatchPredicate = expressionPredicateFactory.create(
                    filter.getNameFilter(),
                    FIELD_PROVIDER,
                    VALUE_FUNCTION_FACTORIES,
                    DateTimeSettings.builder().build());
        }
        return fuzzyMatchPredicate;
    }

    private boolean hasPermission(final FilterableNode filterableNode) {
        return permCheckOutcomeMap.computeIfAbsent(filterableNode.node.getDocRef(), docRef ->
                filter.getRequiredPermissions().stream()
                        .allMatch(permission -> securityContext.hasDocumentPermission(docRef, permission)));
    }

    static boolean hasPermission(final SecurityContext securityContext,
                                 final ExplorerNode node,
                                 final Set<DocumentPermission> requiredPermissions) {
        if (requiredPermissions == null || requiredPermissions.isEmpty()) {
            return false;
        } else {
            return requiredPermissions.stream()
                    .allMatch(permission -> securityContext.hasDocumentPermission(node.getDocRef(), permission));
        }
    }

    boolean isNodeIncluded(final boolean ignoreNameFilter,
                           final ExplorerNode node) {
        return combinedPredicate.test(new FilterableNode(node, ignoreNameFilter));
    }

    private boolean checkType(final FilterableNode filterableNode) {
        return filter.getIncludedTypes().contains(filterableNode.node.getType());
    }

    public ExplorerTreeFilter getFilter() {
        return filter;
    }

    public Set<String> getIncludedTypes() {
        return filter.getIncludedTypes();
    }

    public Set<String> getIncludedRootTypes() {
        return filter.getIncludedRootTypes();
    }

    public Set<String> getTags() {
        return filter.getTags();
    }

    public Set<DocumentPermission> getRequiredPermissions() {
        return filter.getRequiredPermissions();
    }

    public String getNameFilter() {
        return filter.getNameFilter();
    }

    public boolean isNameFilterChange() {
        return filter.isNameFilterChange();
    }


    // --------------------------------------------------------------------------------


    private record FilterableNode(ExplorerNode node, boolean ignoreNameFilter) {

    }


    // --------------------------------------------------------------------------------


    private enum FilterOutcome {
        MATCH(true),
        NON_MATCH(false),
        NO_PREDICATE(true); // No predicate so considered a match

        private final boolean isMatch;

        FilterOutcome(final boolean isMatch) {
            this.isMatch = isMatch;
        }

        static FilterOutcome fromPredicateResult(final boolean predicateResult) {
            return predicateResult
                    ? MATCH
                    : NON_MATCH;
        }

        public boolean isMatch() {
            return isMatch;
        }
    }
}
