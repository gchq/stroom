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

package stroom.dashboard.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class ApplicationInstanceManager implements Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ApplicationInstanceManager.class);

    private static final String CACHE_NAME = "Application Instance";

    private final SecurityContext securityContext;
    private final ICache<String, ApplicationInstance> cache;

    @Inject
    ApplicationInstanceManager(final CacheManager cacheManager,
                               final SecurityContext securityContext,
                               final DashboardConfig dashboardConfig) {
        this.securityContext = securityContext;
        cache = cacheManager
                .create(CACHE_NAME, dashboardConfig::getApplicationInstanceCache, this::create, this::destroy);

        Executors.newScheduledThreadPool(1)
                .scheduleWithFixedDelay(() -> {
                    cache.evictExpiredElements();
                    cache.asMap().values().forEach(ApplicationInstance::keepAlive);
                }, 10, 10, TimeUnit.SECONDS);
    }

    private ApplicationInstance create(final String uuid) {
        final ApplicationInstance applicationInstance =
                new ApplicationInstance(uuid, securityContext.getUserId(), System.currentTimeMillis());
        LOGGER.debug(() -> "Create application instance: " + applicationInstance);
        return applicationInstance;
    }

    public Optional<ApplicationInstance> getOptional(final String uuid) {
        LOGGER.debug(() -> "Getting application instance: " + uuid);
        return cache.getOptional(uuid);
    }

    private void destroy(final String uuid, final ApplicationInstance value) {
        LOGGER.debug(() -> "Destroy application instance: " + value);
        securityContext.asProcessingUser(value::destroy);
    }

    public ApplicationInstance register() {
        final String uuid = UUID.randomUUID().toString();
        final ApplicationInstance applicationInstance = cache.get(uuid);
        LOGGER.debug(() -> "Register new application instance: " + applicationInstance);
        return applicationInstance;
    }

    public boolean keepAlive(final String uuid) {
        final Optional<ApplicationInstance> optional = cache.getOptional(uuid);
        if (optional.isEmpty()) {
            LOGGER.error("Expected application instance not found: " + uuid);
            return false;
        }
        LOGGER.debug(() -> "Keep application instance alive: " + optional.get());
        return true;
    }

    public boolean remove(final String uuid) {
        final Optional<ApplicationInstance> optional = cache.getOptional(uuid);
        if (optional.isEmpty()) {
            LOGGER.error("Expected application instance not found: " + uuid);
            return false;
        }
        LOGGER.debug(() -> "Remove application instance: " + optional.get());
        cache.remove(uuid);
        return true;
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
