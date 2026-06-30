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

package stroom.processor.impl.db;

import stroom.processor.impl.ProcessorProfileDao;
import stroom.processor.shared.FindProcessorProfileRequest;
import stroom.processor.shared.ProcessorProfile;
import stroom.processor.shared.ProfilePeriod;
import stroom.query.api.UserTimeZone;
import stroom.util.shared.ResultPage;
import stroom.util.shared.time.Day;
import stroom.util.shared.time.Days;
import stroom.util.shared.time.Time;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class TestProcessorProfileDaoImpl extends AbstractProcessorTest {

    @Inject
    private ProcessorProfileDao processorProfileDao;

    // ---- Create ----

    @Test
    void testCreate() {
        final ProcessorProfile created = createProfile("testProfile", "group1");

        assertThat(created).isNotNull();
        assertThat(created.getId()).isPositive();
        assertThat(created.getVersion()).isEqualTo(1);
        assertThat(created.getName()).isEqualTo("testProfile");
        assertThat(created.getNodeGroupName()).isEqualTo("group1");
        assertThat(created.getCreateUser()).isEqualTo("test");
        assertThat(created.getUpdateUser()).isEqualTo("test");
    }

    @Test
    void testCreate_withPeriods() {
        final ProfilePeriod period = createAllDayPeriod(10, 5);
        final ProcessorProfile created = createProfileWithPeriods(
                "withPeriods", "group1", List.of(period), UserTimeZone.utc());

        assertThat(created).isNotNull();
        assertThat(created.getProfilePeriods()).hasSize(1);
        assertThat(created.getTimeZone()).isEqualTo(UserTimeZone.utc());

        final ProfilePeriod roundTripped = created.getProfilePeriods().getFirst();
        assertThat(roundTripped.getUuid()).isEqualTo(period.getUuid());
        assertThat(roundTripped.isLimitNodeThreads()).isTrue();
        assertThat(roundTripped.getMaxNodeThreads()).isEqualTo(10);
        assertThat(roundTripped.isLimitClusterThreads()).isTrue();
        assertThat(roundTripped.getMaxClusterThreads()).isEqualTo(5);
    }

    @Test
    void testCreate_withMultiplePeriods() {
        final ProfilePeriod daytime = ProfilePeriod.builder()
                .uuid("period-day")
                .days(Days.create(Set.of(Day.MONDAY, Day.TUESDAY, Day.WEDNESDAY,
                        Day.THURSDAY, Day.FRIDAY)))
                .startTime(new Time(9, 0, 0))
                .endTime(new Time(17, 0, 0))
                .limitNodeThreads(true)
                .maxNodeThreads(5)
                .limitClusterThreads(true)
                .maxClusterThreads(2)
                .build();
        final ProfilePeriod overnight = ProfilePeriod.builder()
                .uuid("period-night")
                .days(Days.create(new HashSet<>(Day.ALL)))
                .startTime(new Time(22, 0, 0))
                .endTime(new Time(6, 0, 0))
                .limitNodeThreads(true)
                .maxNodeThreads(50)
                .limitClusterThreads(false)
                .build();

        final ProcessorProfile created = createProfileWithPeriods(
                "multiPeriod", "group1", List.of(daytime, overnight), UserTimeZone.utc());

        assertThat(created.getProfilePeriods()).hasSize(2);
        assertThat(created.getProfilePeriods().get(0).getUuid()).isEqualTo("period-day");
        assertThat(created.getProfilePeriods().get(1).getUuid()).isEqualTo("period-night");
    }

    // ---- Fetch ----

    @Test
    void testFetchById() {
        final ProcessorProfile created = createProfile("fetchById", "group1");

        final ProcessorProfile fetched = processorProfileDao.fetchById(created.getId());

        assertThat(fetched).isNotNull();
        assertThat(fetched.getId()).isEqualTo(created.getId());
        assertThat(fetched.getName()).isEqualTo("fetchById");
        assertThat(fetched.getNodeGroupName()).isEqualTo("group1");
    }

    @Test
    void testFetchById_notFound() {
        final ProcessorProfile fetched = processorProfileDao.fetchById(99999);
        assertThat(fetched).isNull();
    }

    @Test
    void testFetchByName() {
        createProfile("byName", "group1");

        final ProcessorProfile fetched = processorProfileDao.fetchByName("byName");

        assertThat(fetched).isNotNull();
        assertThat(fetched.getName()).isEqualTo("byName");
    }

    @Test
    void testFetchByName_notFound() {
        final ProcessorProfile fetched = processorProfileDao.fetchByName("nonexistent");
        assertThat(fetched).isNull();
    }

    // ---- Update ----

    @Test
    void testUpdate() {
        final ProcessorProfile created = createProfile("updateMe", "group1");

        final ProcessorProfile updated = processorProfileDao.update(
                created.copy()
                        .name("updatedName")
                        .nodeGroupName("group2")
                        .updateUser("updater")
                        .updateTimeMs(System.currentTimeMillis())
                        .build());

        assertThat(updated.getName()).isEqualTo("updatedName");
        assertThat(updated.getNodeGroupName()).isEqualTo("group2");
        assertThat(updated.getVersion()).isEqualTo(2);
    }

    @Test
    void testUpdate_periods() {
        // Create with one period, then update to have two
        final ProcessorProfile created = createProfileWithPeriods(
                "updatePeriods", "group1",
                List.of(createAllDayPeriod(10, 5)),
                UserTimeZone.utc());

        assertThat(created.getProfilePeriods()).hasSize(1);

        final ProfilePeriod newPeriod = ProfilePeriod.builder()
                .uuid("period-2")
                .days(Days.create(Set.of(Day.SATURDAY, Day.SUNDAY)))
                .startTime(new Time(0, 0, 0))
                .endTime(new Time(23, 59, 59))
                .limitNodeThreads(true)
                .maxNodeThreads(100)
                .limitClusterThreads(false)
                .build();

        final ProcessorProfile updated = processorProfileDao.update(
                created.copy()
                        .profilePeriods(List.of(
                                created.getProfilePeriods().getFirst(),
                                newPeriod))
                        .updateUser("test")
                        .updateTimeMs(System.currentTimeMillis())
                        .build());

        assertThat(updated.getProfilePeriods()).hasSize(2);
        assertThat(updated.getProfilePeriods().get(1).getUuid()).isEqualTo("period-2");
        assertThat(updated.getProfilePeriods().get(1).getMaxNodeThreads()).isEqualTo(100);
    }

    @Test
    void testUpdate_optimisticLocking() {
        // Updating with a stale version should throw
        final ProcessorProfile created = createProfile("lockTest", "group1");

        // First update succeeds
        final ProcessorProfile updated = processorProfileDao.update(
                created.copy()
                        .name("updated")
                        .updateUser("test")
                        .updateTimeMs(System.currentTimeMillis())
                        .build());

        assertThat(updated.getVersion()).isEqualTo(2);

        // Second update with the original (stale) version should fail
        assertThatThrownBy(() -> processorProfileDao.update(
                created.copy()
                        .name("shouldFail")
                        .updateUser("test")
                        .updateTimeMs(System.currentTimeMillis())
                        .build()))
                .isInstanceOf(RuntimeException.class);
    }

    // ---- Delete ----

    @Test
    void testDelete() {
        final ProcessorProfile created = createProfile("deleteMe", "group1");
        assertThat(processorProfileDao.fetchById(created.getId())).isNotNull();

        processorProfileDao.delete(created.getId());

        assertThat(processorProfileDao.fetchById(created.getId())).isNull();
    }

    // ---- Find ----

    @Test
    void testFind_all() {
        createProfile("alpha", "group1");
        createProfile("bravo", "group2");
        createProfile("charlie", "group1");

        final ResultPage<ProcessorProfile> result = processorProfileDao.find(
                new FindProcessorProfileRequest(null, null, null));

        assertThat(result.getValues()).hasSize(3);
    }

    @Test
    void testFind_withFilter() {
        createProfile("prod-indexing", "group1");
        createProfile("prod-search", "group2");
        createProfile("staging-indexing", "group1");

        final ResultPage<ProcessorProfile> result = processorProfileDao.find(
                new FindProcessorProfileRequest(null, null, "prod"));

        assertThat(result.getValues()).hasSize(2);
        assertThat(result.getValues())
                .extracting(ProcessorProfile::getName)
                .allMatch(name -> name.startsWith("prod"));
    }

    @Test
    void testFind_withFilter_noMatch() {
        createProfile("alpha", "group1");

        final ResultPage<ProcessorProfile> result = processorProfileDao.find(
                new FindProcessorProfileRequest(null, null, "zzz"));

        assertThat(result.getValues()).isEmpty();
    }

    // ---- getNames ----

    @Test
    void testGetNames_empty() {
        final List<String> names = processorProfileDao.getNames();
        assertThat(names).isEmpty();
    }

    @Test
    void testGetNames() {
        createProfile("alpha", "group1");
        createProfile("bravo", "group2");
        createProfile("charlie", "group1");

        final List<String> names = processorProfileDao.getNames();

        assertThat(names).containsExactlyInAnyOrder("alpha", "bravo", "charlie");
    }

    // ---- Period round-trip (JSON serialization) ----

    @Test
    void testPeriodRoundTrip_dayFiltering() {
        final ProfilePeriod weekday = ProfilePeriod.builder()
                .uuid("weekday-period")
                .days(Days.create(Set.of(Day.MONDAY, Day.TUESDAY, Day.WEDNESDAY,
                        Day.THURSDAY, Day.FRIDAY)))
                .startTime(new Time(9, 0, 0))
                .endTime(new Time(17, 30, 0))
                .limitNodeThreads(true)
                .maxNodeThreads(20)
                .limitClusterThreads(true)
                .maxClusterThreads(10)
                .build();

        final ProcessorProfile created = createProfileWithPeriods(
                "dayRoundTrip", "group1", List.of(weekday), UserTimeZone.utc());

        // Re-fetch from DB to verify JSON round-trip
        final ProcessorProfile fetched = processorProfileDao.fetchById(created.getId());
        assertThat(fetched.getProfilePeriods()).hasSize(1);

        final ProfilePeriod roundTripped = fetched.getProfilePeriods().getFirst();
        assertThat(roundTripped.getUuid()).isEqualTo("weekday-period");
        assertThat(roundTripped.getDays().isIncluded(Day.MONDAY)).isTrue();
        assertThat(roundTripped.getDays().isIncluded(Day.SATURDAY)).isFalse();
        assertThat(roundTripped.getDays().isIncluded(Day.SUNDAY)).isFalse();
        assertThat(roundTripped.getStartTime()).isEqualTo(new Time(9, 0, 0));
        assertThat(roundTripped.getEndTime()).isEqualTo(new Time(17, 30, 0));
        assertThat(roundTripped.isLimitNodeThreads()).isTrue();
        assertThat(roundTripped.getMaxNodeThreads()).isEqualTo(20);
        assertThat(roundTripped.isLimitClusterThreads()).isTrue();
        assertThat(roundTripped.getMaxClusterThreads()).isEqualTo(10);
    }

    @Test
    void testPeriodRoundTrip_timezone() {
        final UserTimeZone londonTz = UserTimeZone.fromId("Europe/London");

        final ProcessorProfile created = createProfileWithPeriods(
                "tzRoundTrip", "group1",
                List.of(createAllDayPeriod(10, 5)),
                londonTz);

        final ProcessorProfile fetched = processorProfileDao.fetchById(created.getId());
        assertThat(fetched.getTimeZone()).isEqualTo(londonTz);
    }

    @Test
    void testPeriodRoundTrip_noPeriods() {
        final ProcessorProfile created = createProfileWithPeriods(
                "noPeriods", "group1", null, UserTimeZone.utc());

        final ProcessorProfile fetched = processorProfileDao.fetchById(created.getId());
        assertThat(fetched.getProfilePeriods()).isNull();
    }

    // ---- Helpers ----

    private ProcessorProfile createProfile(final String name, final String nodeGroupName) {
        return createProfileWithPeriods(name, nodeGroupName, null, null);
    }

    private ProcessorProfile createProfileWithPeriods(final String name,
                                                       final String nodeGroupName,
                                                       final List<ProfilePeriod> periods,
                                                       final UserTimeZone timeZone) {
        return processorProfileDao.create(
                ProcessorProfile.builder()
                        .name(name)
                        .nodeGroupName(nodeGroupName)
                        .profilePeriods(periods)
                        .timeZone(timeZone)
                        .stampAudit("test")
                        .build());
    }

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
}
