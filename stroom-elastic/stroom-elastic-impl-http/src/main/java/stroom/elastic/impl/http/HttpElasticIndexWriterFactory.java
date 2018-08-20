package stroom.elastic.impl.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.elastic.impl.ElasticIndexConfigCache;
import stroom.elastic.impl.ElasticIndexConfigDoc;
import stroom.elastic.api.ElasticIndexWriter;
import stroom.elastic.api.ElasticIndexWriterFactory;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.security.Security;
import stroom.security.UserService;
import stroom.security.UserTokenUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

@Singleton
class HttpElasticIndexWriterFactory implements ElasticIndexWriterFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpElasticIndexWriterFactory.class);

    private final ElasticIndexConfigCache elasticIndexCache;
    private final Security security;

    @Inject
    HttpElasticIndexWriterFactory(final ElasticIndexConfigCache elasticIndexCache,
                                  final Security security) {
        this.elasticIndexCache = elasticIndexCache;
        this.security = security;
    }

    @Override
    public Optional<ElasticIndexWriter> create(final DocRef elasticConfigRef) {
        return security.asUserResult(UserTokenUtil.create(UserService.STROOM_SERVICE_USER_NAME, null), () -> {
            // Get the index and index fields from the cache.
            final ElasticIndexConfigDoc elasticIndexConfigDoc = elasticIndexCache.get(elasticConfigRef);
            if (elasticIndexConfigDoc == null) {
//                log(Severity.FATAL_ERROR, "Unable to load index", null);
                throw new LoggedException("Unable to load index");
            }

            return Optional.of(new HttpElasticIndexWriter(elasticIndexConfigDoc));
        });
    }
}
