package stroom.elastic.server;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import stroom.elastic.shared.ElasticIndex;
import stroom.elastic.shared.FindElasticIndexCriteria;
import stroom.entity.server.ExternalDocumentEntityServiceImpl;
import stroom.entity.server.util.StroomEntityManager;
import stroom.importexport.server.ImportExportHelper;
import stroom.logging.DocumentEventLog;
import stroom.node.server.StroomPropertyService;
import stroom.security.SecurityContext;

import javax.inject.Inject;

@Component("elasticIndexService")
@Transactional
public class ElasticIndexServiceImpl
        extends ExternalDocumentEntityServiceImpl<ElasticIndex, FindElasticIndexCriteria>
        implements ElasticIndexService {
    @Inject
    ElasticIndexServiceImpl(final StroomEntityManager entityManager,
                            final ImportExportHelper importExportHelper,
                            final SecurityContext securityContext,
                            final DocumentEventLog documentEventLog,
                            final StroomPropertyService propertyService) {
        super(entityManager, importExportHelper, securityContext, documentEventLog, propertyService);
    }

    @Override
    public Class<ElasticIndex> getEntityClass() {
        return ElasticIndex.class;
    }

    @Override
    public FindElasticIndexCriteria createCriteria() {
        return new FindElasticIndexCriteria();
    }
}
