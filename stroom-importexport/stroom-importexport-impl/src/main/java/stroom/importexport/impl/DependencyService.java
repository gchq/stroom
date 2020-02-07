package stroom.importexport.impl;

import stroom.importexport.shared.DependencyCriteria;
import stroom.importexport.shared.DependencyResultPage;

public interface DependencyService {
    DependencyResultPage getDependencies(DependencyCriteria criteria);
}
