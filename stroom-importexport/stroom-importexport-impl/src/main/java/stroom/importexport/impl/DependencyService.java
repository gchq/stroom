package stroom.importexport.impl;

import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.util.shared.ResultPage;

public interface DependencyService {
    ResultPage<Dependency> getDependencies(DependencyCriteria criteria);
}
