/*
 * Copyright 2017 Crown Copyright
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

package stroom.util.cache;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.impl.config.persistence.CacheManagerPersistenceConfiguration;
import org.springframework.stereotype.Component;
import stroom.util.io.FileUtil;
import stroom.util.spring.StroomShutdown;
import stroom.util.spring.StroomStartup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class CentralCacheManager implements AutoCloseable {
    private volatile CacheManager cacheManager;
    private final Map<String, Cache> caches = new ConcurrentHashMap<>();
    private static final AtomicInteger sequence = new AtomicInteger(0);

    @StroomStartup
    public void start() {
        getCacheManager();
    }

    @StroomShutdown
    public void stop() {
        try {
            close();
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private synchronized CacheManager getCacheManager() {
        if (cacheManager == null) {
            try {
                final int no = sequence.incrementAndGet();
                final Path dir = FileUtil.getTempDir()
                        .toPath()
                        .resolve("cache")
                        .resolve(String.valueOf(no));
                if (!Files.isDirectory(dir)) {
                    Files.createDirectories(dir);
                }

                cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
                        .with(new CacheManagerPersistenceConfiguration(dir.toFile()))
                        .build(true);
            } catch (final IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
        return cacheManager;
    }

    @Override
    public synchronized void close() throws Exception {
        if (cacheManager != null) {
            cacheManager.close();
            cacheManager = null;
        }
    }

    public <K, V> Cache<K, V> createCache(final String alias, final CacheConfiguration<K, V> cacheConfiguration) {
        if (caches.containsKey(alias)) {
            throw new RuntimeException("A cache called '" + alias + "' already exists");
        }

        final Cache<K, V> cache = getCacheManager().createCache(alias, cacheConfiguration);
        caches.put(alias, cache);
        return cache;
    }

    public Map<String, Cache> getCaches() {
        return caches;
    }
}
