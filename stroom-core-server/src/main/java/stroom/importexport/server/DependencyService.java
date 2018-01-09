package stroom.importexport.server;

import stroom.entity.shared.BaseResultList;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;

public interface DependencyService {
    BaseResultList<Dependency> getDependencies(DependencyCriteria criteria);
}
