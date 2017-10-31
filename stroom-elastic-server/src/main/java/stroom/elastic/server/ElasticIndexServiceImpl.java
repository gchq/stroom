package stroom.elastic.server;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import stroom.elastic.shared.ElasticIndex;
import stroom.elastic.shared.FindElasticIndexCriteria;
import stroom.entity.server.DocumentEntityServiceImpl;
import stroom.entity.server.util.StroomEntityManager;
import stroom.importexport.server.ImportExportHelper;
import stroom.logging.DocumentEventLog;
import stroom.security.SecurityContext;

import javax.inject.Inject;

@Component("elasticIndexService")
@Transactional
public class ElasticIndexServiceImpl extends DocumentEntityServiceImpl<ElasticIndex, FindElasticIndexCriteria>
        implements ElasticIndexService {
    @Inject
    ElasticIndexServiceImpl(final StroomEntityManager entityManager,
                            final ImportExportHelper importExportHelper,
                            final SecurityContext securityContext,
                            final DocumentEventLog documentEventLog) {
        super(entityManager, importExportHelper, securityContext, documentEventLog);
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
