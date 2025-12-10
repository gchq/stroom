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

package stroom.proxy.app.cache;

import stroom.cache.api.StroomCache;
import stroom.cache.impl.CacheManagerImpl;
import stroom.proxy.repo.ProxyServices;
import stroom.util.HasAdminTasks;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import io.dropwizard.servlets.tasks.Task;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Singleton
public class ProxyCacheServiceImpl implements ProxyCacheService, HasAdminTasks {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyCacheServiceImpl.class);

    private static final Pattern REPLACE_PATTERN = Pattern.compile("[^a-zA-Z0-9.]");

    private final CacheManagerImpl cacheManager;

    @Inject
    public ProxyCacheServiceImpl(final CacheManagerImpl cacheManager,
                                 final ProxyServices proxyServices) {
        this.cacheManager = cacheManager;

        proxyServices.addFrequencyExecutor(
                "Caches - evict expired",
                () -> this::evictExpired,
                Duration.ofMinutes(1).toMillis());
    }

    @Override
    public List<Task> getTasks() {
        final Set<String> cacheNames = cacheManager.getCacheNames();
        final List<Task> tasks = new ArrayList<>(cacheNames.size());
        cacheManager.getCaches().forEach((name, cache) -> {
            final CacheClearTask cacheClearTask = new CacheClearTask(cache);
            tasks.add(cacheClearTask);
        });
        tasks.add(new ClearAllCachesTask(cacheManager));
        return tasks;
    }

    private void evictExpired() {
        try {
            cacheManager.getCaches().forEach((name, cache) -> {
                try {
                    LOGGER.debug("Evicting expired elements in cache '{}'", name);
                    cache.evictExpiredElements();
                } catch (final Exception e) {
                    LOGGER.error("Error calling evictExpiredElements on cache '{}': {}",
                            cache.name(), LogUtil.exceptionMessage(e), e);
                }
            });
        } catch (final Exception e) {
            LOGGER.error("Error running cache eviction: {}", LogUtil.exceptionMessage(e), e);
        }
    }

//    @Override
//    public void start() throws Exception {
//        final TimerTask timerTask = new TimerTask() {
//            @Override
//            public void run() {
//                try {
//                    cacheManager.getCaches().forEach((name, cache) -> {
//                        try {
//                            LOGGER.debug("Evicting expired elements in cache '{}'", name);
//                            cache.evictExpiredElements();
//                        } catch (Exception e) {
//                            LOGGER.error("Error calling evictExpiredElements on cache '{}': {}",
//                                    cache.name(), LogUtil.exceptionMessage(e), e);
//                        }
//                    });
//                } catch (Exception e) {
//                    LOGGER.error("Error running cache eviction timerTask: {}", LogUtil.exceptionMessage(e), e);
//
//                }
//            }
//        };
//
//        LOGGER.info("Starting cache eviction timer");
//        timer.scheduleAtFixedRate(timerTask, 0, 60_000);
//    }

//    @Override
//    public void stop() throws Exception {
//        LOGGER.info("Shutting down cache eviction timer");
//        try {
//            timer.cancel();
//        } catch (Exception e) {
//            LOGGER.error("Error shutting down the timer: {}", LogUtil.exceptionMessage(e), e);
//        }
//    }

//    @Override
//    public Map<String, Metric> getMetrics() {
//        // TODO consider integrating with DropWiz metrics
//        //  https://metrics.dropwizard.io/4.2.0/manual/caffeine.html
//        //  But this would need to be done in AbstractStroomCache
//
//        return cacheManager.getCaches()
//                .entrySet()
//                .stream()
//                .collect(Collectors.toMap(
//                        entry -> HasMetrics.buildName(getClass(), entry.getKey(), HasMetrics.SIZE),
//                        entry -> (Gauge<Long>) () ->
//                                entry.getValue().size()));
//    }

    // --------------------------------------------------------------------------------


    private static class CacheClearTask extends Task {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CacheClearTask.class);

        private final StroomCache<?, ?> cache;

        private CacheClearTask(final StroomCache<?, ?> cache) {
            super(CacheClearTask.buildName(Objects.requireNonNull(cache)));
            this.cache = cache;
        }

        private static String buildName(final StroomCache<?, ?> cache) {
            return "clear-cache-" + REPLACE_PATTERN.matcher(cache.name())
                    .replaceAll("-");
        }

        @Override
        public void execute(final Map<String, List<String>> parameters,
                            final PrintWriter output) throws Exception {
            LOGGER.info("Clearing cache '{}'", cache.name());
            cache.clear();
        }
    }


    // --------------------------------------------------------------------------------


    private static class ClearAllCachesTask extends Task {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CacheClearTask.class);

        private final CacheManagerImpl cacheManager;

        private ClearAllCachesTask(final CacheManagerImpl cacheManager) {
            super("clear-all-caches");
            this.cacheManager = Objects.requireNonNull(cacheManager);
        }

        @Override
        public void execute(final Map<String, List<String>> parameters,
                            final PrintWriter output) {

            LOGGER.info("Clearing all caches");
            cacheManager.getCaches().forEach((name, cache) -> {
                if (cache != null) {
                    LOGGER.info("Clearing cache '{}'", cache.name());
                    cache.clear();
                }
            });
        }
    }
}
