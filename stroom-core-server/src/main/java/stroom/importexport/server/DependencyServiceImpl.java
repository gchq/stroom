package stroom.importexport.server;

import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Sort;
import stroom.entity.shared.Sort.Direction;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.query.api.v2.DocRef;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DependencyServiceImpl implements DependencyService {
    private final ImportExportActionHandlersImpl importExportActionHandlers;

    @Inject
    public DependencyServiceImpl(final ImportExportActionHandlersImpl importExportActionHandlers) {
        this.importExportActionHandlers = importExportActionHandlers;
    }

    @Override
    public BaseResultList<Dependency> getDependencies(final DependencyCriteria criteria) {
        final Map<DocRef, Set<DocRef>> allDependencies = new ConcurrentHashMap<>();
        importExportActionHandlers.getAllHandlers().values().forEach(handler -> {
            final Map<DocRef, Set<DocRef>> deps = handler.getDependencies();
            if (deps != null) {
                allDependencies.putAll(deps);
            }
        });

        final List<Dependency> dependencies = new ArrayList<>();
        allDependencies.forEach((key, value) -> value.forEach(to -> dependencies.add(new Dependency(key, to, allDependencies.containsKey(to)))));

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

        return BaseResultList.createPageLimitedList(dependencies, criteria.getPageRequest());
    }

    private Comparator<DocRef> getDocRefComparator() {
        return Comparator.comparing(DocRef::getType, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(DocRef::getName, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(DocRef::getUuid, Comparator.nullsFirst(Comparator.naturalOrder()));
    }
}
