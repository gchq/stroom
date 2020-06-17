package stroom.importexport.impl;

import stroom.docref.DocRef;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.filter.FilterFieldMapper;
import stroom.util.filter.QuickFilterPredicateFactory;
import stroom.util.shared.CompareUtil;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Sort;
import stroom.util.shared.Sort.Direction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DependencyServiceImpl implements DependencyService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyServiceImpl.class);

    private final ImportExportActionHandlers importExportActionHandlers;
    private final TaskContextFactory taskContextFactory;

    private static final Comparator<Dependency> FROM_TYPE_COMPARATOR =
            CompareUtil.getNullSafeCaseInsensitiveComparator(Dependency::getFrom, DocRef::getType);
    private static final Comparator<Dependency> FROM_NAME_COMPARATOR =
            CompareUtil.getNullSafeCaseInsensitiveComparator(Dependency::getFrom, DocRef::getName);
    private static final Comparator<Dependency> FROM_UUID_COMPARATOR =
            CompareUtil.getNullSafeCaseInsensitiveComparator(Dependency::getFrom, DocRef::getUuid);
    private static final Comparator<Dependency> TO_TYPE_COMPARATOR =
            CompareUtil.getNullSafeCaseInsensitiveComparator(Dependency::getTo, DocRef::getType);
    private static final Comparator<Dependency> TO_NAME_COMPARATOR =
            CompareUtil.getNullSafeCaseInsensitiveComparator(Dependency::getTo, DocRef::getName);
    private static final Comparator<Dependency> TO_UUID_COMPARATOR =
            CompareUtil.getNullSafeCaseInsensitiveComparator(Dependency::getTo, DocRef::getUuid);

    private static final Map<String, Comparator<Dependency>> COMPARATOR_MAP = Map.of(
            DependencyCriteria.FIELD_FROM_TYPE, FROM_TYPE_COMPARATOR,
            DependencyCriteria.FIELD_FROM_NAME, FROM_NAME_COMPARATOR,
            DependencyCriteria.FIELD_FROM_UUID, FROM_UUID_COMPARATOR,
            DependencyCriteria.FIELD_TO_TYPE, TO_TYPE_COMPARATOR,
            DependencyCriteria.FIELD_TO_NAME, TO_NAME_COMPARATOR,
            DependencyCriteria.FIELD_TO_UUID, TO_UUID_COMPARATOR,
            DependencyCriteria.FIELD_STATUS, Comparator.comparing(Dependency::isOk)
    );

    private static final Comparator<Dependency> DEFAULT_COMPARATOR = FROM_TYPE_COMPARATOR
            .thenComparing(FROM_NAME_COMPARATOR)
            .thenComparing(TO_TYPE_COMPARATOR)
            .thenComparing(TO_NAME_COMPARATOR);

    private static final ExpressionOperator SELECT_ALL_EXPRESSION_OP = new ExpressionOperator.Builder(Op.AND).build();

    private static Map<String, FilterFieldMapper<Dependency>> FIELD_MAPPERS = FilterFieldMapper.mappedByQualifier(
            FilterFieldMapper.of(DependencyCriteria.FIELD_DEF_FROM_TYPE, Dependency::getFrom, DocRef::getType),
            FilterFieldMapper.of(DependencyCriteria.FIELD_DEF_FROM_NAME, Dependency::getFrom, DocRef::getName),
            FilterFieldMapper.of(DependencyCriteria.FIELD_DEF_FROM_UUID, Dependency::getFrom, DocRef::getUuid),
            FilterFieldMapper.of(DependencyCriteria.FIELD_DEF_TO_TYPE, Dependency::getTo, DocRef::getType),
            FilterFieldMapper.of(DependencyCriteria.FIELD_DEF_TO_NAME, Dependency::getTo, DocRef::getName),
            FilterFieldMapper.of(DependencyCriteria.FIELD_DEF_TO_UUID, Dependency::getTo, DocRef::getUuid),
            FilterFieldMapper.of(DependencyCriteria.FIELD_DEF_STATUS, Dependency::isOk, bool -> bool ? "OK" : "Missing")
    );

    @Inject
    public DependencyServiceImpl(final ImportExportActionHandlers importExportActionHandlers,
                                 final TaskContextFactory taskContextFactory) {
        this.importExportActionHandlers = importExportActionHandlers;
        this.taskContextFactory = taskContextFactory;
    }

    @Override
    public ResultPage<Dependency> getDependencies(final DependencyCriteria criteria) {
        return taskContextFactory.contextResult(
                "Get Dependencies",
                taskContext -> {
                    try {
                        return getDependencies(criteria, taskContext);
                    } catch (Exception e) {
                        LOGGER.error("Error getting dependencies for criteria " + criteria, e);
                        throw e;
                    }
                })
                .get();
    }

    private ResultPage<Dependency> getDependencies(final DependencyCriteria criteria,
                                                   final TaskContext parentTaskContext) {
        // Build a map of deps (parent to children)
        final Map<DocRef, Set<DocRef>> allDependencies = buildDependencyMap(parentTaskContext);

        final Comparator<Dependency> sortListComparator = getDependencyComparator(criteria);

        final Predicate<Dependency> filterPredicate = buildFilterPredicate(criteria);

        // Flatten the dependency map
        final List<Dependency> flatDependencies = buildFlatDependencies(
                allDependencies,
                sortListComparator,
                filterPredicate);

        return ResultPage.createPageLimitedList(
                flatDependencies,
                Optional.ofNullable(criteria)
                        .map(DependencyCriteria::getPageRequest)
                        .orElse(new PageRequest()));
    }

    private List<Dependency> buildFlatDependencies(final Map<DocRef, Set<DocRef>> allDependencies,
                                                   final Comparator<Dependency> sortListComparator,
                                                   final Predicate<Dependency> filterPredicate) {
        return allDependencies.entrySet().stream()
                .flatMap(entry -> {
                    final DocRef parentDocRef = entry.getKey();
                    final Set<DocRef> childDocRefs = entry.getValue();
                    return childDocRefs.stream()
                            .map(childDocRef -> new Dependency(
                                    parentDocRef,
                                    childDocRef,
                                    allDependencies.containsKey(childDocRef)));
                })
                .filter(filterPredicate)
                .sorted(sortListComparator)
                .collect(Collectors.toList());
    }

    private Map<DocRef, Set<DocRef>> buildDependencyMap(final TaskContext parentTaskContext) {
        return importExportActionHandlers
                .getHandlers()
                .values()
                .parallelStream()
                .map(handler ->
                        taskContextFactory.contextResult(
                                parentTaskContext,
                                "Get " + handler.getType() + " dependencies",
                                taskContext -> {
                                    Map<DocRef, Set<DocRef>> deps = null;
                                    try {
                                        deps = handler.getDependencies();
                                    } catch (final RuntimeException e) {
                                        LOGGER.error(e.getMessage(), e);
                                    }
                                    return deps;
                                }).get())
                .filter(Objects::nonNull)
                .map(Map::entrySet)
                .flatMap(Set::stream)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) ->
                                Stream.concat(e1.stream(), e2.stream())
                                        .collect(Collectors.toSet())));
    }

    private Predicate<Dependency> buildFilterPredicate(final DependencyCriteria criteria) {
        return QuickFilterPredicateFactory.createPredicate(criteria.getPartialName(), FIELD_MAPPERS);

//        final Predicate<Dependency> filterPredicate;
//        if (criteria != null && criteria.getPartialName() != null) {
//            final Predicate<DocRef> docRefPredicate =
//                    DocRefPredicateFactory.createFuzzyMatchPredicate(
//                            criteria.getPartialName(),
//                            MatchMode.NAME_OR_TYPE);
//
//            filterPredicate = dep -> {
//                if (dep == null) {
//                    return false;
//                } else {
//                    // Match on any of {from,to} {name,type}
//                    return docRefPredicate.test(dep.getFrom())
//                            || docRefPredicate.test(dep.getTo());
//                }
//            };
//        } else {
//            filterPredicate = dep -> true;
//        }
//        return filterPredicate;
    }

    private boolean applyPredicate(final Dependency dependency,
                                   final Function<Dependency, DocRef> docRefExtractor,
                                   final Function<DocRef, String> valueExtractor,
                                   final Predicate<String> stringPredicate) {
        final boolean result;
        if (dependency != null) {
            DocRef docRef = docRefExtractor.apply(dependency);
            if (docRef != null) {
                String val = valueExtractor.apply(docRef);
                if (val != null) {
                    result = stringPredicate.test(val);
                } else {
                    // Null val
                    result = false;
                }
            } else {
                // Null docref
                result = false;
            }
        } else {
            // Null dep
            result = false;
        }
        return result;
    }

    private Comparator<Dependency> getDependencyComparator(final DependencyCriteria criteria) {
        // Make the sort comparator base on the criteria sort list
        final Comparator<Dependency> sortListComparator;
        if (criteria != null
                && criteria.getSortList() != null
                && !criteria.getSortList().isEmpty()) {
            sortListComparator = buildComparatorFromSortList(criteria);
        } else {
            sortListComparator = DEFAULT_COMPARATOR;
        }
        return sortListComparator;
    }

    private Comparator<Dependency> buildComparatorFromSortList(
            final DependencyCriteria dependencyCriteria) {

        if (dependencyCriteria != null && !dependencyCriteria.getSortList().isEmpty()) {
            Comparator<Dependency> compositeComparator = null;

            for (final Sort sort : dependencyCriteria.getSortList()) {
                Comparator<Dependency> comparator = COMPARATOR_MAP.get(sort.getField());
                if (comparator != null) {
                    if (Direction.DESCENDING.equals(sort.getDirection())) {
                        comparator = comparator.reversed();
                    }
                    compositeComparator = compositeComparator != null
                            ? compositeComparator.thenComparing(comparator)
                            : comparator;
                }
            }
            return compositeComparator != null
                    ? compositeComparator
                    : Comparator.comparing(dep -> 0);
        } else {
            // Unsorted
            return Comparator.comparing(dep -> 0);
        }
    }

//    private static  Comparator<Dependency> getComparator(
//            final Function<Dependency, DocRef> docRefExtractor,
//            final Function<DocRef, String> valueExtractor) {
//
//        // Sort with nulls first but also handle deps with null docref
//        return Comparator.comparing(
//                docRefExtractor,
//                Comparator.nullsFirst(
//                        Comparator.comparing(
//                                valueExtractor,
//                                Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))));
//    }

}
