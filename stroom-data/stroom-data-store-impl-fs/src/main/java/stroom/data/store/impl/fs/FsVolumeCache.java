package stroom.data.store.impl.fs;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
public class FsVolumeCache implements Clearable {

    private static final String CACHE_NAME = "Volume Cache";
    private final LoadingStroomCache<Integer, FsVolume> cache;
    private final FsVolumeDao fsVolumeDao;

    @Inject
    FsVolumeCache(final CacheManager cacheManager,
                  final Provider<FsVolumeConfig> volumeConfigProvider,
                  final FsVolumeDao fsVolumeDao) {
        this.fsVolumeDao = fsVolumeDao;

        // We have no change handlers due to the complexity of the number of things that can affect this
        // cache, so keep the time short and expire after write, not access.
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> volumeConfigProvider.get().getVolumeCache(),
                this::create);
    }

    public FsVolume get(final int id) {
        return cache.get(id);
    }

    private FsVolume create(final int id) {
        return fsVolumeDao.fetch(id);
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
