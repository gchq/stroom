package stroom.importexport.impl;

import stroom.docref.DocRef;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.util.shared.ResultPage;

import java.util.Map;
import java.util.Set;

public interface DependencyService {

    ResultPage<Dependency> getDependencies(final DependencyCriteria criteria);

    Map<DocRef, Set<DocRef>> getBrokenDependencies();
}
