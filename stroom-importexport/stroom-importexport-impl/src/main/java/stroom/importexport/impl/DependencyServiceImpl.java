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

package stroom.importexport.impl;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.explorer.api.ExplorerDecorator;
import stroom.importexport.api.ImportExportActionHandlers;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.FieldProviderImpl;
import stroom.query.common.v2.SimpleStringExpressionParser.FieldProvider;
import stroom.query.common.v2.ValueFunctionFactoriesImpl;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.CompareUtil;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DependencyServiceImpl implements DependencyService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DependencyServiceImpl.class);

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

    private static final ValueFunctionFactoriesImpl<Dependency>
            VALUE_FUNCTION_FACTORY_MAP = new ValueFunctionFactoriesImpl<Dependency>()
            .put(DependencyCriteria.FIELD_DEF_FROM_TYPE, dep ->
                    NullSafe.get(dep, Dependency::getFrom, DocRef::getType))
            .put(DependencyCriteria.FIELD_DEF_FROM_NAME, dep ->
                    NullSafe.get(dep, Dependency::getFrom, DocRef::getName))
            .put(DependencyCriteria.FIELD_DEF_FROM_UUID, dep ->
                    NullSafe.get(dep, Dependency::getFrom, DocRef::getUuid))
            .put(DependencyCriteria.FIELD_DEF_TO_TYPE, dep ->
                    NullSafe.get(dep, Dependency::getTo, DocRef::getType))
            .put(DependencyCriteria.FIELD_DEF_TO_NAME, dep ->
                    NullSafe.get(dep, Dependency::getTo, DocRef::getName))
            .put(DependencyCriteria.FIELD_DEF_TO_UUID, dep ->
                    NullSafe.get(dep, Dependency::getTo, DocRef::getUuid))
            .put(DependencyCriteria.FIELD_DEF_STATUS, dep ->
                    NullSafe.get(dep,
                            Dependency::isOk,
                            bool -> bool
                                    ? "OK"
                                    : "Missing"));
    private static final FieldProvider FIELD_PROVIDER = new FieldProviderImpl(DependencyCriteria.FIELD_DEFINITIONS);

    private final ImportExportActionHandlers importExportActionHandlers;
    private final DocRefInfoService docRefInfoService;
    private final TaskContextFactory taskContextFactory;
    private final ExplorerDecorator explorerDecorator;
    private final ExpressionPredicateFactory expressionPredicateFactory;

    @Inject
    public DependencyServiceImpl(final ImportExportActionHandlers importExportActionHandlers,
                                 final DocRefInfoService docRefInfoService,
                                 final TaskContextFactory taskContextFactory,
                                 final ExplorerDecorator explorerDecorator,
                                 final ExpressionPredicateFactory expressionPredicateFactory) {
        this.importExportActionHandlers = importExportActionHandlers;
        this.docRefInfoService = docRefInfoService;
        this.taskContextFactory = taskContextFactory;
        this.explorerDecorator = explorerDecorator;
        this.expressionPredicateFactory = expressionPredicateFactory;
    }

    @Override
    public ResultPage<Dependency> getDependencies(final DependencyCriteria criteria) {
        return taskContextFactory.contextResult(
                        "Get Dependencies",
                        taskContext -> {
                            try {
                                return getDependencies(criteria, taskContext);
                            } catch (final Exception e) {
                                LOGGER.error("Error getting dependencies for criteria " + criteria, e);
                                throw e;
                            }
                        })
                .get();
    }

    @Override
    public Map<DocRef, Set<DocRef>> getBrokenDependencies() {
        return taskContextFactory.contextResult(
                        "Get Broken Dependencies",
                        taskContext -> {
                            try {
                                return buildMissingDependencies(taskContext);
                            } catch (final Exception e) {
                                LOGGER.error("Error getting broken dependencies", e);
                                throw e;
                            }
                        })
                .get();
    }

    private ResultPage<Dependency> getDependencies(final DependencyCriteria criteria,
                                                   final TaskContext parentTaskContext) {
        // Build a map of deps (parent to children)
        final Map<DocRef, Set<DocRef>> allDependencies = buildDependencyMap(parentTaskContext);

        final Optional<Comparator<Dependency>> optSortListComparator = getDependencyComparator(criteria);

//        final Predicate<Dependency> filterPredicate = buildFilterPredicate(criteria);

        // Get the additional types that we use to decorate the explorer tree.
        final Set<DocRef> additionalRefs = new HashSet<>(explorerDecorator.list());

        // Flatten the dependency map
        final List<Dependency> flatDependencies = buildFlatDependencies(
                criteria,
                allDependencies,
                additionalRefs,
                optSortListComparator);

        return ResultPage.createPageLimitedList(
                flatDependencies,
                Optional.ofNullable(criteria)
                        .map(DependencyCriteria::getPageRequest)
                        .orElse(new PageRequest()));
    }

    private Map<DocRef, Set<DocRef>> buildMissingDependencies(final TaskContext parentTaskContext) {

        // Parent => children
        final Map<DocRef, Set<DocRef>> allDependencies = buildDependencyMap(parentTaskContext);
        // Get the additional types that we use to decorate the explorer tree.
        final Set<DocRef> additionalRefs = new HashSet<>(explorerDecorator.list());

        return allDependencies.entrySet()
                .stream()
                .filter(entry -> NullSafe.hasItems(entry.getValue()))
                .flatMap(entry -> {
                    final DocRef parentDocRef = entry.getKey();
                    final Set<DocRef> childDocRefs = entry.getValue();
                    return childDocRefs.stream()
                            .map(childDocRef -> Map.entry(parentDocRef, childDocRef));
                })
                .filter(entry -> {
                    // Find ones where the child does not exist, i.e. broken dep
                    final DocRef childDocRef = entry.getValue();
                    return !allDependencies.containsKey(childDocRef)
                           && !additionalRefs.contains(childDocRef);
                })
                .collect(Collectors.groupingBy(
                        Entry::getKey,
                        Collectors.mapping(Entry::getValue, Collectors.toSet())));
    }

    private List<Dependency> buildFlatDependencies(final DependencyCriteria criteria,
                                                   final Map<DocRef, Set<DocRef>> allDependencies,
                                                   final Set<DocRef> pseudoDocRefs,
                                                   final Optional<Comparator<Dependency>> optSortListComparator) {
        final Map<DocRef, Optional<DocRefInfo>> docRefInfoCache = new ConcurrentHashMap<>();
        return expressionPredicateFactory.filterAndSortStream(
                        allDependencies.entrySet()
                                .stream()
                                .flatMap(entry -> {
                                    final DocRef parentDocRef = entry.getKey();
                                    final Set<DocRef> childDocRefs = entry.getValue();
                                    return childDocRefs.stream().map(childDocRef -> {
                                        // Resolve doc info.
                                        final Optional<DocRefInfo> parentInfo = docRefInfoCache
                                                .computeIfAbsent(parentDocRef, docRefInfoService::info);
                                        final Optional<DocRefInfo> childInfo = docRefInfoCache
                                                .computeIfAbsent(childDocRef, docRefInfoService::info);

                                        return new Dependency(
                                                parentInfo.map(DocRefInfo::getDocRef).orElse(parentDocRef),
                                                childInfo.map(DocRefInfo::getDocRef).orElse(childDocRef),
                                                pseudoDocRefs.contains(childDocRef) ||
                                                allDependencies.containsKey(childDocRef));
                                    });
                                }),
                        criteria.getPartialName(),
                        FIELD_PROVIDER,
                        VALUE_FUNCTION_FACTORY_MAP,
                        optSortListComparator)
                .toList();
    }

    private Map<DocRef, Set<DocRef>> buildDependencyMap(final TaskContext parentTaskContext) {
        return importExportActionHandlers
                .getHandlers()
                .values()
                .parallelStream()
                .map(handler ->
                        taskContextFactory.childContextResult(
                                parentTaskContext,
                                "Get " + handler.getType() + " dependencies",
                                taskContext -> {
                                    Map<DocRef, Set<DocRef>> deps = null;
                                    try {
                                        final DurationTimer timer = DurationTimer.start();
                                        deps = handler.getDependencies();
                                        if (LOGGER.isDebugEnabled() && !NullSafe.isEmptyMap(deps)) {
                                            LOGGER.debug("Handler {} returned dependencies for {} docs in {}",
                                                    handler.getClass().getSimpleName(),
                                                    deps.size(),
                                                    timer);
                                        }
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

    private Optional<Comparator<Dependency>> getDependencyComparator(final DependencyCriteria criteria) {
        // Make the sort comparator base on the criteria sort list
        final Optional<Comparator<Dependency>> sortListComparator;
        if (criteria != null
            && criteria.getSortList() != null
            && !criteria.getSortList().isEmpty()) {
            sortListComparator = buildComparatorFromSortList(criteria);
        } else {
            sortListComparator = Optional.empty();
        }
        return sortListComparator;
    }

    private Optional<Comparator<Dependency>> buildComparatorFromSortList(
            final DependencyCriteria dependencyCriteria) {

        if (dependencyCriteria != null && !dependencyCriteria.getSortList().isEmpty()) {
            Comparator<Dependency> compositeComparator = null;

            for (final CriteriaFieldSort sort : dependencyCriteria.getSortList()) {
                Comparator<Dependency> comparator = COMPARATOR_MAP.get(sort.getId());
                if (comparator != null) {
                    if (sort.isDesc()) {
                        comparator = comparator.reversed();
                    }
                    compositeComparator = compositeComparator != null
                            ? compositeComparator.thenComparing(comparator)
                            : comparator;
                }
            }
            return compositeComparator != null
                    ? Optional.of(compositeComparator)
                    : Optional.empty();
        } else {
            // Unsorted
            return Optional.empty();
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
