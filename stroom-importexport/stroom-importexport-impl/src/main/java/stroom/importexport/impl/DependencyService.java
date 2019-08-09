package stroom.importexport.impl;

import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.util.shared.BaseResultList;

public interface DependencyService {
    BaseResultList<Dependency> getDependencies(DependencyCriteria criteria);
}
