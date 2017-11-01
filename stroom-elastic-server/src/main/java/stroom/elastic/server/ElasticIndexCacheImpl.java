package stroom.elastic.server;

import net.sf.ehcache.CacheManager;
import org.springframework.stereotype.Component;
import stroom.cache.AbstractCacheBean;
import stroom.elastic.shared.ElasticIndex;
import stroom.query.api.v2.DocRef;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

@Component
public class ElasticIndexCacheImpl extends AbstractCacheBean<DocRef, ElasticIndex> implements ElasticIndexCache {
    private static final int MAX_CACHE_ENTRIES = 100;

    private final ElasticIndexService indexService;

    @Inject
    ElasticIndexCacheImpl(final CacheManager cacheManager,
                          final ElasticIndexService indexService) {
        super(cacheManager, "Elastic Index Config Cache", MAX_CACHE_ENTRIES);
        this.indexService = indexService;

        setMaxIdleTime(10, TimeUnit.MINUTES);
        setMaxLiveTime(10, TimeUnit.MINUTES);
    }

    @Override
    public ElasticIndex getOrCreate(final DocRef key) {
        return computeIfAbsent(key, this::create);
    }

    private ElasticIndex create(final DocRef key) {
        final ElasticIndex loaded = indexService.loadByUuid(key.getUuid());
        if (loaded == null) {
            throw new NullPointerException("No index can be found for: " + key);
        }

        return loaded;
    }
}
