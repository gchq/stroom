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

package stroom.processor.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.node.api.NodeGroupCache;
import stroom.processor.shared.ProcessorProfile;
import stroom.processor.shared.ProfilePeriod;
import stroom.query.language.functions.UserTimeZoneUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.NullSafe;
import stroom.util.shared.time.Day;
import stroom.util.shared.time.Time;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Singleton
public class ProcessorProfileCache implements Clearable {

    private static final String CACHE_NAME = "Processor Profile Cache";
    private static final ProfileResult ZERO = new ProfileResult(0, 0);

    private final LoadingStroomCache<String, Optional<ProcessorProfile>> cache;
    private final ProcessorProfileDao processorProfileDao;
    private final NodeGroupCache nodeGroupCache;

    @Inject
    public ProcessorProfileCache(final CacheManager cacheManager,
                                 final ProcessorProfileDao processorProfileDao,
                                 final Provider<ProcessorConfig> processorConfigProvider,
                                 final NodeGroupCache nodeGroupCache) {
        this.processorProfileDao = processorProfileDao;
        this.nodeGroupCache = nodeGroupCache;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> processorConfigProvider.get().getProcessorProfileCache(),
                this::create);
    }

    public Optional<ProcessorProfile> get(final String name) {
        return cache.get(name);
    }

    private Optional<ProcessorProfile> create(final String name) {
        return Optional.ofNullable(processorProfileDao.fetchByName(name));
    }

    @Override
    public void clear() {
        cache.clear();
    }

    public ProfileResult getProfile(final String node, final String profileName) {
        final Optional<ProcessorProfile> optionalProcessorProfile = get(profileName);
        final ProcessorProfile processorProfile = optionalProcessorProfile.orElseThrow(() ->
                new RuntimeException("Processor profile called '" +
                                     profileName +
                                     "' not found"));
        final Optional<Set<String>> optionalNodeGroupData = nodeGroupCache
                .getIncludedGroupNodes(processorProfile.getNodeGroupName());
        final Set<String> includedGroupNodes = optionalNodeGroupData.orElseThrow(() ->
                new RuntimeException("No node group called '" +
                                     processorProfile.getNodeGroupName() +
                                     "' can be found for processor profile '" +
                                     processorProfile.getName() +
                                     "'"));

        if (!includedGroupNodes.contains(node)) {
            return ZERO;
        }

        // If there are no periods defined then return zero.
        final List<ProfilePeriod> periods = processorProfile.getProfilePeriods();
        if (NullSafe.isEmptyCollection(periods)) {
            return ZERO;
        }

        // Get the configured time zone for the periods.
        final Instant now = Instant.now();
        final ZoneId zoneId = UserTimeZoneUtil.getZoneId(processorProfile.getTimeZone(), null);
        final ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(now, zoneId);
        final int dayValue = zonedDateTime.getDayOfWeek().getValue();
        final Day day = Day.getDayByValue(dayValue);

        for (final ProfilePeriod period : periods) {
            // Ignore periods that do not include the current day.
            if (period.getDays().isIncluded(day)) {
                // Check start time is before or equal.
                final ZonedDateTime startTime = setTime(zonedDateTime, period.getStartTime());
                if (startTime.isBefore(zonedDateTime) || startTime.equals(zonedDateTime)) {
                    // Check end time is after or equal.
                    final ZonedDateTime endTime = setTime(zonedDateTime, period.getEndTime());
                    if (endTime.isAfter(zonedDateTime) || endTime.equals(zonedDateTime)) {
                        int maxNodeTasks = Integer.MAX_VALUE;
                        if (period.isLimitNodeThreads()) {
                            maxNodeTasks = period.getMaxNodeThreads();
                        }
                        int maxClusterTasks = Integer.MAX_VALUE;
                        if (period.isLimitClusterThreads()) {
                            maxClusterTasks = period.getMaxClusterThreads();
                        }
                        return new ProfileResult(maxNodeTasks, maxClusterTasks);
                    }
                }
            }
        }

        return ZERO;
    }

    private ZonedDateTime setTime(final ZonedDateTime zonedDateTime, final Time time) {
        return zonedDateTime.withHour(time.getHour()).withMinute(time.getMinute()).withSecond(time.getSecond());
    }

    public record ProfileResult(int maxNodeTasks, int maxClusterTasks) {

    }
}
