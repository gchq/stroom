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
import stroom.util.NullSafe;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class ApplicationInstanceManager implements Clearable, HasSystemInfo {

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

    public void keepAlive(final String uuid) {
        final Optional<ApplicationInstance> optional = cache.getOptional(uuid);
        if (optional.isEmpty()) {
            throw new RuntimeException("Expected application instance not found: " + uuid);
        }
        LOGGER.debug(() -> "Keep application instance alive: " + optional.get());
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

    @Override
    public SystemInfoResult getSystemInfo() {

        final SystemInfoResult.Builder builder = SystemInfoResult.builder(this);

        final Map<String, List<ApplicationInstance>> groupedData = cache.asMap()
                .values()
                .stream()
                .collect(Collectors.groupingBy(ApplicationInstance::getUserId));

        groupedData.forEach((userId, applicationInstances) -> {
            final List<Map<String, Object>> detailMaps = new ArrayList<>();
            applicationInstances.forEach(appInst -> {
                final Map<String, Object> detailMap = new HashMap<>();

                detailMap.put("applicationInstanceId", appInst.getUuid());
                detailMap.put("createTime", DateUtil.createNormalDateTimeString(appInst.getCreateTime()));
                detailMap.put("age", Duration.between(Instant.ofEpochMilli(appInst.getCreateTime()), Instant.now())
                        .toString());
                detailMap.put("activeQueryCount", appInst.getActiveQueries().count());
                detailMap.put("activeQueries", NullSafe.getOrElse(
                                appInst,
                                ApplicationInstance::getActiveQueries,
                                ActiveQueries::asList,
                                Collections.<ActiveQuery>emptyList())
                        .stream()
                        .map(activeQuery -> {
                            final Map<String, Object> activeQueryDetailMap = new HashMap<>();
                            activeQueryDetailMap.put("queryKey", activeQuery.getQueryKey().toString());
                            activeQueryDetailMap.put(
                                    "datasourceProviderType",
                                    activeQuery.getDataSourceProvider().getType());
                            activeQueryDetailMap.put("docref", activeQuery.getDocRef());
                            activeQueryDetailMap.put(
                                    "createTime",
                                    DateUtil.createNormalDateTimeString(activeQuery.getCreationTime()));
                            activeQueryDetailMap.put(
                                    "age",
                                    Duration.between(Instant.ofEpochMilli(activeQuery.getCreationTime()), Instant.now())
                                            .toString());
                            return activeQueryDetailMap;
                        })
                        .collect(Collectors.toList()));

                detailMaps.add(detailMap);
            });
            builder.addDetail(userId, detailMaps);
        });
        return builder.build();
    }
}
