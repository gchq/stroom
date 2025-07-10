package stroom.importexport.impl;

import stroom.docref.DocRef;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.importexport.shared.ContentResourceV1;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.importexport.shared.ExportContentRequest;
import stroom.importexport.shared.ImportConfigRequest;
import stroom.importexport.shared.ImportConfigResponse;
import stroom.util.shared.DocRefs;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.util.Set;

/**
 * Deprecated. Latest is {@link ContentResourceImpl}
 */
@Deprecated
@AutoLogged
public class ContentResourceV1Impl implements ContentResourceV1 {

    final ContentResourceImpl contentResourceImpl;

    @Inject
    ContentResourceV1Impl(final ContentResourceImpl contentResourceImpl) {
        this.contentResourceImpl = contentResourceImpl;
    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public ImportConfigResponse importContent(final ImportConfigRequest request) {
        return contentResourceImpl.importContent(request);
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public ResourceGeneration exportContent(final DocRefs docRefs) {
        final Set<DocRef> docRefSet = NullSafe.get(docRefs, DocRefs::getDocRefs);
        final ExportContentRequest exportContentRequest = new ExportContentRequest(
                docRefSet, true);
        return contentResourceImpl.exportContent(exportContentRequest);
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public ResultPage<Dependency> fetchDependencies(final DependencyCriteria criteria) {
        return contentResourceImpl.fetchDependencies(criteria);
    }
}
