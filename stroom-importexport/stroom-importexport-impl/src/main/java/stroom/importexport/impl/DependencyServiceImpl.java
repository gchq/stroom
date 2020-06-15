package stroom.importexport.impl;

import stroom.docref.DocRef;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DependencyServiceImpl implements DependencyService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyServiceImpl.class);

    private final ImportExportActionHandlers importExportActionHandlers;
    private final TaskContextFactory taskContextFactory;

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

        if (criteria.getSortList() == null || criteria.getSortList().size() == 0) {
            criteria.addSort(new Sort(DependencyCriteria.FIELD_FROM, Direction.ASCENDING, true));
            criteria.addSort(new Sort(DependencyCriteria.FIELD_TO, Direction.ASCENDING, true));
            criteria.addSort(new Sort(DependencyCriteria.FIELD_STATUS, Direction.ASCENDING, true));
        }

        final Comparator<DocRef> docRefComparator = getDocRefComparator();
        dependencies.sort((o1, o2) -> {
            int diff = 0;

            for (final Sort sort : criteria.getSortList()) {
                switch (sort.getField()) {
                    case DependencyCriteria.FIELD_FROM:
                        diff = docRefComparator.compare(o1.getFrom(), o2.getFrom());
                        break;
                    case DependencyCriteria.FIELD_TO:
                        diff = docRefComparator.compare(o1.getTo(), o2.getTo());
                        break;
                    case DependencyCriteria.FIELD_STATUS:
                        diff = Boolean.compare(o1.isOk(), o2.isOk());
                        break;
                }

                if (Direction.DESCENDING.equals(sort.getDirection())) {
                    diff = diff * -1;
                }

                if (diff != 0) {
                    return diff;
                }
            }

            return diff;
        });

        return ResultPage.createPageLimitedList(dependencies, criteria.getPageRequest());
    }

    private Comparator<DocRef> getDocRefComparator() {
        return Comparator.comparing(DocRef::getType, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(DocRef::getName, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(DocRef::getUuid, Comparator.nullsFirst(Comparator.naturalOrder()));
    }
}
