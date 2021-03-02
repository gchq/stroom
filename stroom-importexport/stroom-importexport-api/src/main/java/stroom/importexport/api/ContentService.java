package stroom.importexport.api;

import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.importexport.shared.ImportState;
import stroom.util.shared.DocRefs;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.ResultPage;

import java.util.List;

public interface ContentService {

    ResourceKey performImport(final ResourceKey resourceKey, final List<ImportState> confirmList);

    List<ImportState> confirmImport(final ResourceKey resourceKey);

    ResourceGeneration exportContent(final DocRefs docRefs);

    ResultPage<Dependency> fetchDependencies(final DependencyCriteria criteria);

}
