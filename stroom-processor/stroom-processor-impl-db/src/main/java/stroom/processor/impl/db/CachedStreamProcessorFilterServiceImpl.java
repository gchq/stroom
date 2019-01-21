package stroom.processor.impl.db;

import stroom.entity.CachingEntityManager;
import stroom.persist.EntityManagerSupport;
import stroom.processor.StreamProcessorService;
import stroom.security.Security;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class CachedStreamProcessorFilterServiceImpl extends StreamProcessorFilterServiceImpl implements CachedStreamProcessorFilterService {
    @Inject
    CachedStreamProcessorFilterServiceImpl(final CachingEntityManager entityManager, final Security security, final EntityManagerSupport entityManagerSupport, final StreamProcessorService streamProcessorService) {
        super(entityManager, security, entityManagerSupport, streamProcessorService);
    }
}
