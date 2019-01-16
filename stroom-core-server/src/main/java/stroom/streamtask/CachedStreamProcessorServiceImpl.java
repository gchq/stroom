package stroom.streamtask;

import stroom.entity.CachingEntityManager;
import stroom.security.Security;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class CachedStreamProcessorServiceImpl extends StreamProcessorServiceImpl implements CachedStreamProcessorService {
    @Inject
    CachedStreamProcessorServiceImpl(final CachingEntityManager entityManager, final Security security) {
        super(entityManager, security);
    }
}
