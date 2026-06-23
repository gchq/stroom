/*
 * Copyright 2016-2026 Crown Copyright
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
import stroom.node.api.NodeGroupInfo;
import stroom.node.shared.NodeGroup;
import stroom.processor.impl.ProcessorProfileCache.ProfileResult;
import stroom.processor.shared.ProcessorProfile;
import stroom.processor.shared.ProfilePeriod;
import stroom.query.api.UserTimeZone;
import stroom.util.shared.time.Day;
import stroom.util.shared.time.Days;
import stroom.util.shared.time.Time;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestProcessorProfileCache {

    private static final ProfileResult ZERO = new ProfileResult(0, 0);
    private static final String NODE_NAME = "node1";
    private static final String PROFILE_NAME = "myProfile";
    private static final String GROUP_NAME = "myGroup";

    // A fixed instant: Wednesday 2025-06-18 at 14:30:00 UTC
    private static final Instant WEDNESDAY_14_30 = ZonedDateTime.of(
            2025, 6, 18, 14, 30, 0, 0, ZoneOffset.UTC).toInstant();

    @Mock
    private CacheManager cacheManager;
    @Mock
    private ProcessorProfileDao processorProfileDao;
    @Mock
    private NodeGroupCache nodeGroupCache;
    @Mock
    private LoadingStroomCache<String, Optional<ProcessorProfile>> profileCache;

    private ProcessorProfileCache processorProfileCache;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        // Capture the load function so we can wire the mock cache to delegate to it
        final ArgumentCaptor<Function<String, Optional<ProcessorProfile>>> loadFunctionCaptor =
                ArgumentCaptor.forClass(Function.class);

        when(cacheManager.<String, Optional<ProcessorProfile>>createLoadingCache(
                anyString(), any(), loadFunctionCaptor.capture()))
                .thenReturn(profileCache);

        final ProcessorConfig config = new ProcessorConfig();
        processorProfileCache = new ProcessorProfileCache(
                cacheManager,
                processorProfileDao,
                () -> config,
                nodeGroupCache);

        // Wire the mock cache's get() to delegate to the captured load function
        // so that the cache behaves like a real pass-through loading cache
        final Function<String, Optional<ProcessorProfile>> loadFunction = loadFunctionCaptor.getValue();
        when(profileCache.get(anyString())).thenAnswer(invocation -> {
            final String key = invocation.getArgument(0);
            return loadFunction.apply(key);
        });
    }

    // ---- Disabled node group ----

    @Test
    void testGetProfile_disabledNodeGroup_returnsZero() {
        // A profile referencing a disabled node group should always return ZERO,
        // even if the node is included in the group and periods are configured.
        setupProfile(PROFILE_NAME, GROUP_NAME, createAllDayPeriod(10, 5));
        setupNodeGroup(GROUP_NAME, false, Set.of(NODE_NAME));

        final ProfileResult result = processorProfileCache.getProfile(
                NODE_NAME, PROFILE_NAME, WEDNESDAY_14_30);

        assertThat(result).isEqualTo(ZERO);
    }

    @Test
    void testGetProfile_disabledNodeGroup_nodeNotInGroup_returnsZero() {
        // Even if the node is not in the group, the disabled check comes first
        setupProfile(PROFILE_NAME, GROUP_NAME, createAllDayPeriod(10, 5));
        setupNodeGroup(GROUP_NAME, false, Set.of("otherNode"));

        final ProfileResult result = processorProfileCache.getProfile(
                NODE_NAME, PROFILE_NAME, WEDNESDAY_14_30);

        assertThat(result).isEqualTo(ZERO);
    }

    @Test
    void testGetProfile_disabledNodeGroup_emptyGroup_returnsZero() {
        setupProfile(PROFILE_NAME, GROUP_NAME, createAllDayPeriod(10, 5));
        setupNodeGroup(GROUP_NAME, false, Set.of());

        final ProfileResult result = processorProfileCache.getProfile(
                NODE_NAME, PROFILE_NAME, WEDNESDAY_14_30);

        assertThat(result).isEqualTo(ZERO);
    }

    // ---- Node not in group ----

    @Test
    void testGetProfile_nodeNotInGroup_returnsZero() {
        setupProfile(PROFILE_NAME, GROUP_NAME, createAllDayPeriod(10, 5));
        setupNodeGroup(GROUP_NAME, true, Set.of("node2", "node3"));

        final ProfileResult result = processorProfileCache.getProfile(
                NODE_NAME, PROFILE_NAME, WEDNESDAY_14_30);

        assertThat(result).isEqualTo(ZERO);
    }

    @Test
    void testGetProfile_emptyGroup_returnsZero() {
        setupProfile(PROFILE_NAME, GROUP_NAME, createAllDayPeriod(10, 5));
        setupNodeGroup(GROUP_NAME, true, Set.of());

        final ProfileResult result = processorProfileCache.getProfile(
                NODE_NAME, PROFILE_NAME, WEDNESDAY_14_30);

        assertThat(result).isEqualTo(ZERO);
    }

    // ---- Node in enabled group with valid period ----

    @Test
    void testGetProfile_nodeInEnabledGroup_withMatchingPeriod_returnsTaskLimits() {
        final int maxNodeThreads = 10;
        final int maxClusterThreads = 5;
        setupProfile(PROFILE_NAME, GROUP_NAME, createAllDayPeriod(maxNodeThreads, maxClusterThreads));
        setupNodeGroup(GROUP_NAME, true, Set.of(NODE_NAME));

        final ProfileResult result = processorProfileCache.getProfile(
                NODE_NAME, PROFILE_NAME, WEDNESDAY_14_30);

        assertThat(result.maxNodeTasks()).isEqualTo(maxNodeThreads);
        assertThat(result.maxClusterTasks()).isEqualTo(maxClusterThreads);
    }

    @Test
    void testGetProfile_nodeInEnabledGroup_unlimitedPeriod_returnsMaxValue() {
        final ProfilePeriod unlimitedPeriod = ProfilePeriod.builder()
                .uuid("period-1")
                .days(Days.create(new HashSet<>(Day.ALL)))
                .startTime(new Time(0, 0, 0))
                .endTime(new Time(23, 59, 59))
                .limitNodeThreads(false)
                .limitClusterThreads(false)
                .build();
        setupProfile(PROFILE_NAME, GROUP_NAME, unlimitedPeriod);
        setupNodeGroup(GROUP_NAME, true, Set.of(NODE_NAME));

        final ProfileResult result = processorProfileCache.getProfile(
                NODE_NAME, PROFILE_NAME, WEDNESDAY_14_30);

        assertThat(result.maxNodeTasks()).isEqualTo(Integer.MAX_VALUE);
        assertThat(result.maxClusterTasks()).isEqualTo(Integer.MAX_VALUE);
    }

    // ---- No periods ----

    @Test
    void testGetProfile_noPeriods_returnsZero() {
        final ProcessorProfile profile = ProcessorProfile.builder()
                .name(PROFILE_NAME)
                .nodeGroupName(GROUP_NAME)
                .timeZone(UserTimeZone.utc())
                .stampAudit("test")
                .build();
        when(processorProfileDao.fetchByName(PROFILE_NAME)).thenReturn(profile);
        setupNodeGroup(GROUP_NAME, true, Set.of(NODE_NAME));

        final ProfileResult result = processorProfileCache.getProfile(
                NODE_NAME, PROFILE_NAME, WEDNESDAY_14_30);

        assertThat(result).isEqualTo(ZERO);
    }

    // ---- Period day filtering ----

    @Test
    void testGetProfile_periodOnDifferentDay_returnsZero() {
        // WEDNESDAY_14_30 is a Wednesday — a Monday-only period should not match
        final ProfilePeriod mondayOnly = ProfilePeriod.builder()
                .uuid("period-1")
                .days(Days.create(Set.of(Day.MONDAY)))
                .startTime(new Time(0, 0, 0))
                .endTime(new Time(23, 59, 59))
                .limitNodeThreads(true)
                .maxNodeThreads(10)
                .limitClusterThreads(true)
                .maxClusterThreads(5)
                .build();
        setupProfile(PROFILE_NAME, GROUP_NAME, mondayOnly);
        setupNodeGroup(GROUP_NAME, true, Set.of(NODE_NAME));

        final ProfileResult result = processorProfileCache.getProfile(
                NODE_NAME, PROFILE_NAME, WEDNESDAY_14_30);

        assertThat(result).isEqualTo(ZERO);
    }

    @Test
    void testGetProfile_periodMatchesCurrentDay() {
        // Wednesday period should match WEDNESDAY_14_30
        final ProfilePeriod wednesdayPeriod = ProfilePeriod.builder()
                .uuid("period-1")
                .days(Days.create(Set.of(Day.WEDNESDAY)))
                .startTime(new Time(0, 0, 0))
                .endTime(new Time(23, 59, 59))
                .limitNodeThreads(true)
                .maxNodeThreads(7)
                .limitClusterThreads(true)
                .maxClusterThreads(3)
                .build();
        setupProfile(PROFILE_NAME, GROUP_NAME, wednesdayPeriod);
        setupNodeGroup(GROUP_NAME, true, Set.of(NODE_NAME));

        final ProfileResult result = processorProfileCache.getProfile(
                NODE_NAME, PROFILE_NAME, WEDNESDAY_14_30);

        assertThat(result.maxNodeTasks()).isEqualTo(7);
        assertThat(result.maxClusterTasks()).isEqualTo(3);
    }

    // ---- Period time window filtering ----

    @Test
    void testGetProfile_beforePeriodStartTime_returnsZero() {
        // WEDNESDAY_14_30 — period starts at 15:00, should not match
        final ProfilePeriod afterNow = ProfilePeriod.builder()
                .uuid("period-1")
                .days(Days.create(Set.of(Day.WEDNESDAY)))
                .startTime(new Time(15, 0, 0))
                .endTime(new Time(23, 59, 59))
                .limitNodeThreads(true)
                .maxNodeThreads(10)
                .limitClusterThreads(true)
                .maxClusterThreads(5)
                .build();
        setupProfile(PROFILE_NAME, GROUP_NAME, afterNow);
        setupNodeGroup(GROUP_NAME, true, Set.of(NODE_NAME));

        final ProfileResult result = processorProfileCache.getProfile(
                NODE_NAME, PROFILE_NAME, WEDNESDAY_14_30);

        assertThat(result).isEqualTo(ZERO);
    }

    @Test
    void testGetProfile_afterPeriodEndTime_returnsZero() {
        // WEDNESDAY_14_30 — period ends at 14:00, should not match
        final ProfilePeriod beforeNow = ProfilePeriod.builder()
                .uuid("period-1")
                .days(Days.create(Set.of(Day.WEDNESDAY)))
                .startTime(new Time(8, 0, 0))
                .endTime(new Time(14, 0, 0))
                .limitNodeThreads(true)
                .maxNodeThreads(10)
                .limitClusterThreads(true)
                .maxClusterThreads(5)
                .build();
        setupProfile(PROFILE_NAME, GROUP_NAME, beforeNow);
        setupNodeGroup(GROUP_NAME, true, Set.of(NODE_NAME));

        final ProfileResult result = processorProfileCache.getProfile(
                NODE_NAME, PROFILE_NAME, WEDNESDAY_14_30);

        assertThat(result).isEqualTo(ZERO);
    }

    @Test
    void testGetProfile_withinPeriodTimeWindow_returnsLimits() {
        // WEDNESDAY_14_30 — period 09:00 to 18:00, should match
        final ProfilePeriod workingHours = ProfilePeriod.builder()
                .uuid("period-1")
                .days(Days.create(Set.of(Day.WEDNESDAY)))
                .startTime(new Time(9, 0, 0))
                .endTime(new Time(18, 0, 0))
                .limitNodeThreads(true)
                .maxNodeThreads(20)
                .limitClusterThreads(true)
                .maxClusterThreads(10)
                .build();
        setupProfile(PROFILE_NAME, GROUP_NAME, workingHours);
        setupNodeGroup(GROUP_NAME, true, Set.of(NODE_NAME));

        final ProfileResult result = processorProfileCache.getProfile(
                NODE_NAME, PROFILE_NAME, WEDNESDAY_14_30);

        assertThat(result.maxNodeTasks()).isEqualTo(20);
        assertThat(result.maxClusterTasks()).isEqualTo(10);
    }

    // ---- Overnight period (end time before start time) ----

    @Test
    void testGetProfile_overnightPeriod_withinWindow() {
        // Period 22:00 - 06:00 (overnight). Test at 23:00 Wednesday.
        final Instant wednesday23 = ZonedDateTime.of(
                2025, 6, 18, 23, 0, 0, 0, ZoneOffset.UTC).toInstant();

        final ProfilePeriod overnightPeriod = ProfilePeriod.builder()
                .uuid("period-1")
                .days(Days.create(Set.of(Day.WEDNESDAY)))
                .startTime(new Time(22, 0, 0))
                .endTime(new Time(6, 0, 0))
                .limitNodeThreads(true)
                .maxNodeThreads(50)
                .limitClusterThreads(true)
                .maxClusterThreads(25)
                .build();
        setupProfile(PROFILE_NAME, GROUP_NAME, overnightPeriod);
        setupNodeGroup(GROUP_NAME, true, Set.of(NODE_NAME));

        final ProfileResult result = processorProfileCache.getProfile(
                NODE_NAME, PROFILE_NAME, wednesday23);

        assertThat(result.maxNodeTasks()).isEqualTo(50);
        assertThat(result.maxClusterTasks()).isEqualTo(25);
    }

    @Test
    void testGetProfile_overnightPeriod_beforeWindow() {
        // Period 22:00 - 06:00. Test at 14:30 — should NOT match.
        final ProfilePeriod overnightPeriod = ProfilePeriod.builder()
                .uuid("period-1")
                .days(Days.create(Set.of(Day.WEDNESDAY)))
                .startTime(new Time(22, 0, 0))
                .endTime(new Time(6, 0, 0))
                .limitNodeThreads(true)
                .maxNodeThreads(50)
                .limitClusterThreads(true)
                .maxClusterThreads(25)
                .build();
        setupProfile(PROFILE_NAME, GROUP_NAME, overnightPeriod);
        setupNodeGroup(GROUP_NAME, true, Set.of(NODE_NAME));

        final ProfileResult result = processorProfileCache.getProfile(
                NODE_NAME, PROFILE_NAME, WEDNESDAY_14_30);

        assertThat(result).isEqualTo(ZERO);
    }

    // ---- Multiple periods (first match wins) ----

    @Test
    void testGetProfile_multiplePeriods_firstMatchWins() {
        // Two periods: daytime (09-17) with low limits, evening (17-23) with high limits.
        // At 14:30 the daytime period should match first.
        final ProfilePeriod daytime = ProfilePeriod.builder()
                .uuid("period-day")
                .days(Days.create(new HashSet<>(Day.ALL)))
                .startTime(new Time(9, 0, 0))
                .endTime(new Time(17, 0, 0))
                .limitNodeThreads(true)
                .maxNodeThreads(5)
                .limitClusterThreads(true)
                .maxClusterThreads(2)
                .build();
        final ProfilePeriod evening = ProfilePeriod.builder()
                .uuid("period-eve")
                .days(Days.create(new HashSet<>(Day.ALL)))
                .startTime(new Time(17, 0, 0))
                .endTime(new Time(23, 0, 0))
                .limitNodeThreads(true)
                .maxNodeThreads(50)
                .limitClusterThreads(true)
                .maxClusterThreads(20)
                .build();

        setupProfileWithPeriods(PROFILE_NAME, GROUP_NAME, List.of(daytime, evening));
        setupNodeGroup(GROUP_NAME, true, Set.of(NODE_NAME));

        final ProfileResult result = processorProfileCache.getProfile(
                NODE_NAME, PROFILE_NAME, WEDNESDAY_14_30);

        assertThat(result.maxNodeTasks()).isEqualTo(5);
        assertThat(result.maxClusterTasks()).isEqualTo(2);
    }

    @Test
    void testGetProfile_multiplePeriods_secondMatchUsed() {
        // Same two periods as above, but test at 19:00 — evening period should match.
        final Instant wednesday19 = ZonedDateTime.of(
                2025, 6, 18, 19, 0, 0, 0, ZoneOffset.UTC).toInstant();

        final ProfilePeriod daytime = ProfilePeriod.builder()
                .uuid("period-day")
                .days(Days.create(new HashSet<>(Day.ALL)))
                .startTime(new Time(9, 0, 0))
                .endTime(new Time(17, 0, 0))
                .limitNodeThreads(true)
                .maxNodeThreads(5)
                .limitClusterThreads(true)
                .maxClusterThreads(2)
                .build();
        final ProfilePeriod evening = ProfilePeriod.builder()
                .uuid("period-eve")
                .days(Days.create(new HashSet<>(Day.ALL)))
                .startTime(new Time(17, 0, 0))
                .endTime(new Time(23, 0, 0))
                .limitNodeThreads(true)
                .maxNodeThreads(50)
                .limitClusterThreads(true)
                .maxClusterThreads(20)
                .build();

        setupProfileWithPeriods(PROFILE_NAME, GROUP_NAME, List.of(daytime, evening));
        setupNodeGroup(GROUP_NAME, true, Set.of(NODE_NAME));

        final ProfileResult result = processorProfileCache.getProfile(
                NODE_NAME, PROFILE_NAME, wednesday19);

        assertThat(result.maxNodeTasks()).isEqualTo(50);
        assertThat(result.maxClusterTasks()).isEqualTo(20);
    }

    @Test
    void testGetProfile_multiplePeriods_noneMatch_returnsZero() {
        // Two daytime periods but test at 02:00 — neither should match.
        final Instant wednesday02 = ZonedDateTime.of(
                2025, 6, 18, 2, 0, 0, 0, ZoneOffset.UTC).toInstant();

        final ProfilePeriod morning = ProfilePeriod.builder()
                .uuid("period-am")
                .days(Days.create(new HashSet<>(Day.ALL)))
                .startTime(new Time(8, 0, 0))
                .endTime(new Time(12, 0, 0))
                .limitNodeThreads(true)
                .maxNodeThreads(10)
                .limitClusterThreads(true)
                .maxClusterThreads(5)
                .build();
        final ProfilePeriod afternoon = ProfilePeriod.builder()
                .uuid("period-pm")
                .days(Days.create(new HashSet<>(Day.ALL)))
                .startTime(new Time(13, 0, 0))
                .endTime(new Time(18, 0, 0))
                .limitNodeThreads(true)
                .maxNodeThreads(20)
                .limitClusterThreads(true)
                .maxClusterThreads(10)
                .build();

        setupProfileWithPeriods(PROFILE_NAME, GROUP_NAME, List.of(morning, afternoon));
        setupNodeGroup(GROUP_NAME, true, Set.of(NODE_NAME));

        final ProfileResult result = processorProfileCache.getProfile(
                NODE_NAME, PROFILE_NAME, wednesday02);

        assertThat(result).isEqualTo(ZERO);
    }

    // ---- Mixed thread limits ----

    @Test
    void testGetProfile_nodeLimitedOnly() {
        final ProfilePeriod period = ProfilePeriod.builder()
                .uuid("period-1")
                .days(Days.create(new HashSet<>(Day.ALL)))
                .startTime(new Time(0, 0, 0))
                .endTime(new Time(23, 59, 59))
                .limitNodeThreads(true)
                .maxNodeThreads(15)
                .limitClusterThreads(false)
                .build();
        setupProfile(PROFILE_NAME, GROUP_NAME, period);
        setupNodeGroup(GROUP_NAME, true, Set.of(NODE_NAME));

        final ProfileResult result = processorProfileCache.getProfile(
                NODE_NAME, PROFILE_NAME, WEDNESDAY_14_30);

        assertThat(result.maxNodeTasks()).isEqualTo(15);
        assertThat(result.maxClusterTasks()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void testGetProfile_clusterLimitedOnly() {
        final ProfilePeriod period = ProfilePeriod.builder()
                .uuid("period-1")
                .days(Days.create(new HashSet<>(Day.ALL)))
                .startTime(new Time(0, 0, 0))
                .endTime(new Time(23, 59, 59))
                .limitNodeThreads(false)
                .limitClusterThreads(true)
                .maxClusterThreads(8)
                .build();
        setupProfile(PROFILE_NAME, GROUP_NAME, period);
        setupNodeGroup(GROUP_NAME, true, Set.of(NODE_NAME));

        final ProfileResult result = processorProfileCache.getProfile(
                NODE_NAME, PROFILE_NAME, WEDNESDAY_14_30);

        assertThat(result.maxNodeTasks()).isEqualTo(Integer.MAX_VALUE);
        assertThat(result.maxClusterTasks()).isEqualTo(8);
    }

    // ---- Weekday-specific periods ----

    @Test
    void testGetProfile_weekdayVsWeekend_weekdayMatches() {
        // Weekday period with low limits, weekend period with high limits.
        // Wednesday should match weekday.
        final ProfilePeriod weekday = ProfilePeriod.builder()
                .uuid("period-weekday")
                .days(Days.create(Set.of(Day.MONDAY, Day.TUESDAY, Day.WEDNESDAY,
                        Day.THURSDAY, Day.FRIDAY)))
                .startTime(new Time(0, 0, 0))
                .endTime(new Time(23, 59, 59))
                .limitNodeThreads(true)
                .maxNodeThreads(5)
                .limitClusterThreads(true)
                .maxClusterThreads(2)
                .build();
        final ProfilePeriod weekend = ProfilePeriod.builder()
                .uuid("period-weekend")
                .days(Days.create(Set.of(Day.SATURDAY, Day.SUNDAY)))
                .startTime(new Time(0, 0, 0))
                .endTime(new Time(23, 59, 59))
                .limitNodeThreads(true)
                .maxNodeThreads(100)
                .limitClusterThreads(true)
                .maxClusterThreads(50)
                .build();

        setupProfileWithPeriods(PROFILE_NAME, GROUP_NAME, List.of(weekday, weekend));
        setupNodeGroup(GROUP_NAME, true, Set.of(NODE_NAME));

        final ProfileResult result = processorProfileCache.getProfile(
                NODE_NAME, PROFILE_NAME, WEDNESDAY_14_30);

        assertThat(result.maxNodeTasks()).isEqualTo(5);
        assertThat(result.maxClusterTasks()).isEqualTo(2);
    }

    @Test
    void testGetProfile_weekdayVsWeekend_weekendMatches() {
        // Same periods but test on Saturday at 14:30
        final Instant saturday14_30 = ZonedDateTime.of(
                2025, 6, 21, 14, 30, 0, 0, ZoneOffset.UTC).toInstant();

        final ProfilePeriod weekday = ProfilePeriod.builder()
                .uuid("period-weekday")
                .days(Days.create(Set.of(Day.MONDAY, Day.TUESDAY, Day.WEDNESDAY,
                        Day.THURSDAY, Day.FRIDAY)))
                .startTime(new Time(0, 0, 0))
                .endTime(new Time(23, 59, 59))
                .limitNodeThreads(true)
                .maxNodeThreads(5)
                .limitClusterThreads(true)
                .maxClusterThreads(2)
                .build();
        final ProfilePeriod weekend = ProfilePeriod.builder()
                .uuid("period-weekend")
                .days(Days.create(Set.of(Day.SATURDAY, Day.SUNDAY)))
                .startTime(new Time(0, 0, 0))
                .endTime(new Time(23, 59, 59))
                .limitNodeThreads(true)
                .maxNodeThreads(100)
                .limitClusterThreads(true)
                .maxClusterThreads(50)
                .build();

        setupProfileWithPeriods(PROFILE_NAME, GROUP_NAME, List.of(weekday, weekend));
        setupNodeGroup(GROUP_NAME, true, Set.of(NODE_NAME));

        final ProfileResult result = processorProfileCache.getProfile(
                NODE_NAME, PROFILE_NAME, saturday14_30);

        assertThat(result.maxNodeTasks()).isEqualTo(100);
        assertThat(result.maxClusterTasks()).isEqualTo(50);
    }

    // ---- Error cases ----

    @Test
    void testGetProfile_profileNotFound_throws() {
        when(processorProfileDao.fetchByName("unknown")).thenReturn(null);

        assertThatThrownBy(() -> processorProfileCache.getProfile("node1", "unknown", WEDNESDAY_14_30))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unknown")
                .hasMessageContaining("not found");
    }

    @Test
    void testGetProfile_nodeGroupNotFound_throws() {
        setupProfile(PROFILE_NAME, "missingGroup", createAllDayPeriod(10, 5));
        when(nodeGroupCache.getIncludedGroupNodes("missingGroup")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> processorProfileCache.getProfile("node1", PROFILE_NAME, WEDNESDAY_14_30))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("missingGroup");
    }

    // ---- getProfile(profileName, instant) overload (no node check) ----

    @Test
    void testGetProfile_byNameOnly_skipsNodeGroupCheck() {
        setupProfile(PROFILE_NAME, GROUP_NAME, createAllDayPeriod(8, 4));

        final ProfileResult result = processorProfileCache.getProfile(PROFILE_NAME, WEDNESDAY_14_30);

        assertThat(result.maxNodeTasks()).isEqualTo(8);
        assertThat(result.maxClusterTasks()).isEqualTo(4);
    }

    @Test
    void testGetProfile_byNameOnly_periodNotMatching_returnsZero() {
        // Period only on Monday, but instant is Wednesday
        final ProfilePeriod mondayOnly = ProfilePeriod.builder()
                .uuid("period-1")
                .days(Days.create(Set.of(Day.MONDAY)))
                .startTime(new Time(0, 0, 0))
                .endTime(new Time(23, 59, 59))
                .limitNodeThreads(true)
                .maxNodeThreads(10)
                .limitClusterThreads(true)
                .maxClusterThreads(5)
                .build();
        setupProfile(PROFILE_NAME, GROUP_NAME, mondayOnly);

        final ProfileResult result = processorProfileCache.getProfile(PROFILE_NAME, WEDNESDAY_14_30);

        assertThat(result).isEqualTo(ZERO);
    }

    // ---- Helpers ----

    /**
     * Creates a ProfilePeriod that covers all days 00:00:00 - 23:59:59
     * with the specified thread limits.
     */
    private ProfilePeriod createAllDayPeriod(final int maxNodeThreads,
                                             final int maxClusterThreads) {
        return ProfilePeriod.builder()
                .uuid("period-1")
                .days(Days.create(new HashSet<>(Day.ALL)))
                .startTime(new Time(0, 0, 0))
                .endTime(new Time(23, 59, 59))
                .limitNodeThreads(true)
                .maxNodeThreads(maxNodeThreads)
                .limitClusterThreads(true)
                .maxClusterThreads(maxClusterThreads)
                .build();
    }

    private void setupProfile(final String profileName,
                               final String groupName,
                               final ProfilePeriod period) {
        setupProfileWithPeriods(profileName, groupName, List.of(period));
    }

    private void setupProfileWithPeriods(final String profileName,
                                          final String groupName,
                                          final List<ProfilePeriod> periods) {
        final ProcessorProfile profile = ProcessorProfile.builder()
                .name(profileName)
                .nodeGroupName(groupName)
                .profilePeriods(periods)
                .timeZone(UserTimeZone.utc())
                .stampAudit("test")
                .build();
        when(processorProfileDao.fetchByName(profileName)).thenReturn(profile);
    }

    private void setupNodeGroup(final String groupName,
                                 final boolean enabled,
                                 final Set<String> includedNodes) {
        final NodeGroup nodeGroup = NodeGroup.builder()
                .name(groupName)
                .enabled(enabled)
                .stampAudit("test")
                .build();
        when(nodeGroupCache.getIncludedGroupNodes(groupName))
                .thenReturn(Optional.of(new NodeGroupInfo(nodeGroup, includedNodes)));
    }
}
