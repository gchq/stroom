/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
