package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.NodeFlag;
import stroom.security.api.SecurityContext;
import stroom.util.NullSafe;
import stroom.util.PredicateUtil;
import stroom.util.filter.FilterFieldMapper;
import stroom.util.filter.FilterFieldMappers;
import stroom.util.filter.QuickFilterPredicateFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.GwtNullSafe;
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
    private static final FilterFieldMappers<DocRef> FIELD_MAPPERS = FilterFieldMappers.of(
            FilterFieldMapper.of(ExplorerTreeFilter.FIELD_DEF_NAME, DocRef::getName),
            FilterFieldMapper.of(ExplorerTreeFilter.FIELD_DEF_TYPE, DocRef::getType),
            FilterFieldMapper.of(ExplorerTreeFilter.FIELD_DEF_UUID, DocRef::getUuid));

    private final SecurityContext securityContext;
    private final ExplorerTreeFilter filter;
    private final Map<DocRef, FilterOutcome> filterOutcomeMap = new HashMap<>();
    private final Map<DocRef, Boolean> permCheckOutcomeMap = new HashMap<>();
    private final boolean hasNameFilter;

    private final Predicate<FilterableNode> combinedPredicate;
    private Predicate<DocRef> fuzzyMatchPredicate = null;


    NodeInclusionChecker(final SecurityContext securityContext,
                         final ExplorerTreeFilter filter) {
        this.securityContext = securityContext;

        this.filter = Objects.requireNonNull(filter);
        this.hasNameFilter = !NullSafe.isBlankString(filter.getNameFilter());
        // We can build a single predicate based on what is in the ExplorerTreeFilter,
        // e.g. not doing a type check if no types set is provided.
        // The predicate is called for potentially every node, so it needs to be efficient.
        // For typical tree use, only the perm check will be active.
        this.combinedPredicate = buildCombinedPredicate(filter);
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
            predicates.add(buildTagFilterPredicate(filterTags));
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
            final Set<String> requiredPermissions = filter.getRequiredPermissions();
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

    private static Predicate<FilterableNode> buildTagFilterPredicate(final Set<String> filterTags) {
        return filterableNode -> {
            // TODO: 18/09/2023 Change tags to be a set
            if (GwtNullSafe.isBlankString(filterableNode.node.getTags())) {
                // No tags
                return false;
            } else {
                final Set<String> nodeTags = NullSafe.asSet(filterableNode.node.getTags()
                        .split(ExplorerNode.TAGS_DELIMITER));
                return nodeTags.containsAll(filterTags);
            }
        };
    }

    private boolean testWithNameFilter(final FilterableNode filterableNode) {
        return filterableNode.ignoreNameFilter
                || isFuzzyFilterMatch(filterableNode.node.getDocRef());
    }

    private boolean isFuzzyFilterMatch(final DocRef docRef) {
        return filterOutcomeMap.computeIfAbsent(docRef, docRef2 ->
                FilterOutcome.fromPredicateResult(fuzzyMatchPredicate.test(docRef2)))
                .isMatch;
    }

    boolean isFuzzyFilterMatch(final ExplorerNode node) {
        return !hasNameFilter || filterOutcomeMap.computeIfAbsent(node.getDocRef(), docRef ->
                FilterOutcome.fromPredicateResult(fuzzyMatchPredicate.test(docRef)))
                .isMatch;
    }

    Predicate<DocRef> getFuzzyMatchPredicate() {
        if (fuzzyMatchPredicate == null) {
            // Create the predicate for the current filter value, lazily as there may not be one
            fuzzyMatchPredicate = QuickFilterPredicateFactory.createFuzzyMatchPredicate(
                    filter.getNameFilter(), FIELD_MAPPERS);
        }
        return fuzzyMatchPredicate;
    }

    String getQualifiedNameFilterInput() {
        return QuickFilterPredicateFactory.fullyQualifyInput(filter.getNameFilter(), FIELD_MAPPERS);
    }

    private boolean hasPermission(final FilterableNode filterableNode) {
        return permCheckOutcomeMap.computeIfAbsent(filterableNode.node.getDocRef(), docRef -> {
            final String uuid = docRef.getUuid();
            return filter.getRequiredPermissions().stream()
                    .allMatch(permission -> securityContext.hasDocumentPermission(uuid, permission));
        });
    }

    static boolean hasPermission(final SecurityContext securityContext,
                                 final ExplorerNode node,
                                 final Set<String> requiredPermissions) {
        if (requiredPermissions == null || requiredPermissions.isEmpty()) {
            return false;
        } else {
            final String uuid = node.getUuid();
            return requiredPermissions.stream()
                    .allMatch(permission -> securityContext.hasDocumentPermission(uuid, permission));
        }
    }

    boolean isNodeIncluded(final boolean ignoreNameFilter,
                           final ExplorerNode node) {
        return combinedPredicate.test(new FilterableNode(node, ignoreNameFilter));
    }

    private boolean checkType(final FilterableNode filterableNode) {
        return filter.getIncludedTypes().contains(filterableNode.node.getType());
    }

    private boolean checkTags(final FilterableNode filterableNode) {
        final Set<String> filterTagsSet = filter.getTags();
        final String nodeTagsStr = filterableNode.node.getTags();

        // TODO: 14/09/2023 Doing a simple string contains is pretty flawed if one tag is a substring of another.
        //  It should be using an agreed delimiter (e.g. space) or better still have the node hold a set of
        //  tags. Also should do a case insensitive comparison.
        //  so a non-issue for now.
        for (final String tag : filterTagsSet) {
            if (nodeTagsStr != null && nodeTagsStr.contains(tag)) {
                return true;
            }
        }
        return false;
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

    public Set<String> getRequiredPermissions() {
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
