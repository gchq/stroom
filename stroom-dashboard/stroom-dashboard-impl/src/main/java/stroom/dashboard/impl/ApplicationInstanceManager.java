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
import stroom.cache.api.StroomCache;
import stroom.security.api.SecurityContext;
import stroom.util.NullSafe;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class ApplicationInstanceManager implements Clearable, HasSystemInfo {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ApplicationInstanceManager.class);
    private static final String CACHE_NAME = "Application Instance";

    private final SecurityContext securityContext;
    private final StroomCache<String, ApplicationInstance> cache;
    // To aid debugging of the destruction of app
    private final ConcurrentMap<String, AppInstanceDebugInfo> appInstanceDebugMap = new ConcurrentHashMap<>();

    @Inject
    ApplicationInstanceManager(final CacheManager cacheManager,
                               final SecurityContext securityContext,
                               final Provider<DashboardConfig> dashboardConfigProvider) {
        this.securityContext = securityContext;
        cache = cacheManager.create(
                CACHE_NAME,
                () -> dashboardConfigProvider.get().getApplicationInstanceCache(),
                this::destroy);
    }

    /**
     * This gets called periodically, so we can check if DEBUG logging is on and enable/disable
     * recording of debug info
     */
    public void evictExpiredElements() {
        try {
            if (LOGGER.isDebugEnabled()) {
                // Avoid cost/locks that come with getting the size
                final long cacheSizeBefore = cache.size();
                cache.evictExpiredElements();

                LOGGER.debug(() ->
                        LogUtil.message("Evicting expired elements, cache size: " +
                                        "{} (before eviction) {} (after eviction)",
                                cacheSizeBefore, cache.size()));

                // Now dump cache contents
                cache.forEach((uuid, applicationInstance) -> {
                    // Record the keep alive time and app instance details for later inspection
                    final AppInstanceDebugInfo debugInfo = appInstanceDebugMap
                            .compute(uuid, (uuid2, appInstanceDebugInfo) -> {
                                final Instant keepAliveTime = Instant.now();
                                return appInstanceDebugInfo == null
                                        ? new AppInstanceDebugInfo(applicationInstance, null, keepAliveTime)
                                        : appInstanceDebugInfo.withKeepAliveTime(Instant.now());
                            });
                    dumpRecordedDebugInfo(uuid, debugInfo, "evictExpiredElements");
                });
            } else {
                cache.evictExpiredElements();
                appInstanceDebugMap.clear();
            }
        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    public Optional<ApplicationInstance> getOptApplicationInstance(final String uuid) {
        final Optional<ApplicationInstance> optApplicationInstance = cache.getOptional(uuid);
        optApplicationInstance.ifPresentOrElse(
                applicationInstance ->
                        LOGGER.debug(() -> LogUtil.message("Getting application instance: {}",
                                applicationInstanceToStr(applicationInstance))),
                () -> {
                    LOGGER.warn(() -> LogUtil.message(
                            "Missing application instance for user: {}. Their session may have timed out. " +
                                    "Enable debug logging for more info.",
                            securityContext.getUserId()));

                    if (LOGGER.isDebugEnabled()) {
                        final AppInstanceDebugInfo debugInfo = appInstanceDebugMap.get(uuid);
                        dumpRecordedDebugInfo(uuid, debugInfo, "getOptApplicationInstance");
                    }
                });
        return optApplicationInstance;
    }

    private void dumpRecordedDebugInfo(final String uuid,
                                       final AppInstanceDebugInfo debugInfo,
                                       final String message) {
        if (debugInfo != null) {
            LOGGER.debug("Debug info - {} - uuid: {}, user: {}, createTime: {} (age: {}), " +
                            "destroyTime: {}, age at death: {}, lastKeepAlive: {}",
                    message,
                    debugInfo.uuid,
                    debugInfo.userId,
                    debugInfo.createTime,
                    NullSafe.toStringOrElse(
                            debugInfo.createTime,
                            createTime2 -> Duration.between(createTime2, Instant.now()),
                            "N/A"),
                    debugInfo.destroyTime,
                    NullSafe.toStringOrElse(
                            debugInfo.destroyTime,
                            destroyTime2 -> Duration.between(debugInfo.createTime, destroyTime2),
                            "N/A"),
                    debugInfo.lastKeepAliveTime);
        } else {
            LOGGER.debug("No debug info for application instance {}. Maybe DEBUG wasn't on when " +
                    "it was destroyed.", uuid);
        }
    }

    private ApplicationInstance create(final String uuid) {
        final ApplicationInstance applicationInstance =
                new ApplicationInstance(uuid, securityContext.getUserId(), System.currentTimeMillis());
        LOGGER.debug(() -> "Create application instance: " + applicationInstanceToStr(applicationInstance));
        return applicationInstance;
    }


    private void destroy(final String uuid, final ApplicationInstance applicationInstance) {
        // This may be due to explicit removal from the cache or eviction due to aging off.
        // Aging off should only occur if the user's browser dies and wasn't able to send an explicit
        // remove call before it died.
        LOGGER.debug(() -> LogUtil.message("Destroying application instance {}",
                applicationInstanceToStr(applicationInstance)));

        if (LOGGER.isDebugEnabled()) {
            // Record the app instance along with the destroy time so we can later check what it was
            final AppInstanceDebugInfo debugInfo = appInstanceDebugMap.compute(uuid, (uuid2, appInstanceDebugInfo) -> {
                final Instant destroyTime = Instant.now();
                return appInstanceDebugInfo == null
                        ? new AppInstanceDebugInfo(applicationInstance, destroyTime, null)
                        : appInstanceDebugInfo.withDestroyTime(destroyTime);
            });
            dumpRecordedDebugInfo(uuid, debugInfo, "destroy");
        }

        securityContext.asProcessingUser(applicationInstance::destroy);
    }

    private String applicationInstanceToStr(final ApplicationInstance applicationInstance) {
        if (applicationInstance == null) {
            return "null";
        } else {
            return LogUtil.message("uuid: {}, user: {}, createTime: {} (age: {}), activeQuery count: {}",
                    applicationInstance.getUuid(),
                    applicationInstance.getUserId(),
                    DateUtil.createNormalDateTimeString(applicationInstance.getCreateTime()),
                    Duration.ofMillis(System.currentTimeMillis() - applicationInstance.getCreateTime()),
                    applicationInstance.getActiveQueries().count());
        }
    }

    public ApplicationInstance register() {
        final String uuid = UUID.randomUUID().toString();
        // Create and cache a new ApplicationInstance
        final ApplicationInstance applicationInstance = cache.get(uuid, this::create);
        LOGGER.debug(() -> LogUtil.message("Register new application instance: {}",
                applicationInstanceToStr(applicationInstance)));
        return applicationInstance;
    }

    /**
     * Touch the cache entry for the supplied {@link ApplicationInstance} uuid to keep it active
     * in the cache and consequently for the {@link ApplicationInstance} to keep any downstream
     * interests alive.
     */
    public void keepAlive(final String uuid) {
        LOGGER.trace(() -> "KeepAlive called for application instance " + uuid);
        final Optional<ApplicationInstance> optApplicationInstance = cache.getOptional(uuid);
        if (LOGGER.isDebugEnabled()) {
            dumpRecordedDebugInfo(uuid, appInstanceDebugMap.get(uuid), "keepAlive");
        }
        if (optApplicationInstance.isEmpty()) {
            throw new RuntimeException("Expected application instance not found: " + uuid);
        } else {
            optApplicationInstance.get().keepAlive();
        }
        LOGGER.debug(() -> "Client called keepAlive for application instance: "
                + applicationInstanceToStr(optApplicationInstance.get()));
    }

    public boolean remove(final String uuid) {
        LOGGER.trace(() -> "Remove called for application instance " + uuid);
        return cache.getOptional(uuid)
                .map(applicationInstance -> {
                    LOGGER.debug(() -> "Explicitly remove application instance from cache: " +
                            applicationInstanceToStr(applicationInstance));
                    cache.remove(uuid);
                    return true;
                })
                .orElseGet(() -> {
                    LOGGER.error("Expected application instance not found, uuid: " + uuid);
                    return false;
                });
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public SystemInfoResult getSystemInfo() {

        final SystemInfoResult.Builder builder = SystemInfoResult.builder(this);

        final Map<String, List<ApplicationInstance>> groupedData = cache.values()
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

    private static class AppInstanceDebugInfo {

        private final String uuid;
        private final String userId;
        private final Instant createTime;
        private final Instant destroyTime;
        private final Instant lastKeepAliveTime;

        private AppInstanceDebugInfo(final ApplicationInstance applicationInstance,
                                     final Instant destroyTime,
                                     final Instant keepAliveTime) {
            this(
                    applicationInstance.getUuid(),
                    applicationInstance.getUserId(),
                    Instant.ofEpochMilli(applicationInstance.getCreateTime()),
                    destroyTime,
                    keepAliveTime);
        }

        private AppInstanceDebugInfo(final String uuid,
                                     final String userId,
                                     final Instant createTime,
                                     final Instant destroyTime,
                                     final Instant lastKeepAliveTime) {
            this.uuid = uuid;
            this.userId = userId;
            this.createTime = createTime;
            this.destroyTime = destroyTime;
            this.lastKeepAliveTime = lastKeepAliveTime;
        }

        public AppInstanceDebugInfo withKeepAliveTime(final Instant keepAliveTime) {
            return new AppInstanceDebugInfo(
                    uuid,
                    userId,
                    createTime,
                    destroyTime,
                    keepAliveTime);
        }

        public AppInstanceDebugInfo withDestroyTime(final Instant destroyTime) {
            return new AppInstanceDebugInfo(
                    uuid,
                    userId,
                    createTime,
                    destroyTime,
                    lastKeepAliveTime);
        }

        @Override
        public String toString() {
            final String ageAtDeathStr = NullSafe.toStringOrElse(
                    destroyTime,
                    destroyTime2 -> Duration.between(createTime, destroyTime2),
                    "?");

            return "AppInstanceDebugInfo{" +
                    "uuid='" + uuid + '\'' +
                    ", userId='" + userId + '\'' +
                    ", createTime=" + createTime +
                    ", destroyTime=" + destroyTime +
                    ", age at death=" + ageAtDeathStr +
                    ", lastKeepAliveTime=" + lastKeepAliveTime +
                    '}';
        }
    }
}
