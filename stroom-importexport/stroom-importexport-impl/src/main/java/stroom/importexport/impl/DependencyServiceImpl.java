package stroom.importexport.impl;

import stroom.docref.DocRef;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Sort;
import stroom.util.shared.Sort.Direction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DependencyServiceImpl implements DependencyService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyServiceImpl.class);

    private final ImportExportActionHandlers importExportActionHandlers;
    private final TaskContextFactory taskContextFactory;

    private static final Comparator<Dependency> FROM_TYPE_COMPARATOR = getComparator(Dependency::getFrom, DocRef::getType);
    private static final Comparator<Dependency> FROM_NAME_COMPARATOR = getComparator(Dependency::getFrom, DocRef::getName);
    private static final Comparator<Dependency> FROM_UUID_COMPARATOR = getComparator(Dependency::getFrom, DocRef::getUuid);
    private static final Comparator<Dependency> TO_TYPE_COMPARATOR = getComparator(Dependency::getTo, DocRef::getType);
    private static final Comparator<Dependency> TO_NAME_COMPARATOR = getComparator(Dependency::getTo, DocRef::getName);
    private static final Comparator<Dependency> TO_UUID_COMPARATOR = getComparator(Dependency::getTo, DocRef::getUuid);

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

    @Inject
    public DependencyServiceImpl(final ImportExportActionHandlers importExportActionHandlers,
                                 final TaskContextFactory taskContextFactory) {
        this.importExportActionHandlers = importExportActionHandlers;
        this.taskContextFactory = taskContextFactory;
    }

    @Override
    public ResultPage<Dependency> getDependencies(final DependencyCriteria criteria) {
        return taskContextFactory.contextResult("Get Dependencies", taskContext ->
                getDependencies(criteria, taskContext)).get();
    }

    private ResultPage<Dependency> getDependencies(final DependencyCriteria criteria,
                                                   final TaskContext parentTaskContext) {
        final Map<DocRef, Set<DocRef>> allDependencies = importExportActionHandlers
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

        final List<Dependency> dependencies = new ArrayList<>();
        allDependencies.forEach((key, value) ->
                value.forEach(to ->
                        dependencies.add(new Dependency(key, to, allDependencies.containsKey(to)))));

        final Comparator<Dependency> sortListComparator;
        if (criteria != null && criteria.getSortList() != null && !criteria.getSortList().isEmpty()) {
            sortListComparator = buildComparatorFromSortList(criteria);
        } else {
            sortListComparator = DEFAULT_COMPARATOR;
        }

        dependencies.sort(sortListComparator);

        return ResultPage.createPageLimitedList(
                dependencies,
                Optional.ofNullable(criteria)
                        .map(DependencyCriteria::getPageRequest)
                        .orElse(new PageRequest()));
    }

    private Comparator<Dependency> buildComparatorFromSortList(final DependencyCriteria dependencyCriteria) {
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

    private static  Comparator<Dependency> getComparator(
            final Function<Dependency, DocRef> docRefExtractor,
            final Function<DocRef, String> valueExtractor) {

        // Sort with nulls first but also handle deps with null docref
        return Comparator.comparing(
                docRefExtractor,
                Comparator.nullsFirst(
                        Comparator.comparing(
                                valueExtractor,
                                Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))));
    }
}
