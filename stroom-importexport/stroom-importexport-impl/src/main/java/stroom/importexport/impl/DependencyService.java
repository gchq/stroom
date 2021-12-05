package stroom.importexport.impl;

import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.util.shared.QuickFilterResultPage;

public interface DependencyService {

    QuickFilterResultPage<Dependency> getDependencies(final DependencyCriteria criteria);
}
