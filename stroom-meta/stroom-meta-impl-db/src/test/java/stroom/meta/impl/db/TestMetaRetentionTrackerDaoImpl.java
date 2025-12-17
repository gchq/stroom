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

package stroom.meta.impl.db;

import stroom.cache.impl.CacheModule;
import stroom.cluster.lock.mock.MockClusterLockModule;
import stroom.collection.mock.MockCollectionModule;
import stroom.data.retention.api.DataRetentionTracker;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.dictionary.mock.MockWordListProviderModule;
import stroom.docrefinfo.mock.MockDocRefInfoModule;
import stroom.security.mock.MockSecurityContextModule;
import stroom.task.mock.MockTaskModule;
import stroom.test.common.MockMetricsModule;
import stroom.test.common.util.db.DbTestModule;

import com.google.inject.Guice;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestMetaRetentionTrackerDaoImpl {

    @Inject
    private Cleanup cleanup;
    @Inject
    private MetaRetentionTrackerDaoImpl metaRetentionTrackerDao;

    @BeforeEach
    void setup() {
        Guice.createInjector(
                        new MetaTestModule(),
                        new MetaDbModule(),
                        new MetaDaoModule(),
                        new MockClusterLockModule(),
                        new MockSecurityContextModule(),
                        new MockTaskModule(),
                        new MockCollectionModule(),
                        new MockDocRefInfoModule(),
                        new MockWordListProviderModule(),
                        new MockMetricsModule(),
                        new CacheModule(),
                        new DbTestModule())
                .injectMembers(this);

        // Delete everything
        cleanup.cleanup();
    }

    @Test
    void testGet_doesntExist() {

        final List<DataRetentionTracker> trackers = metaRetentionTrackerDao.getTrackers();

        assertThat(trackers).isEmpty();
    }

    @Test
    void testCreateThenUpdate() {

        final List<DataRetentionTracker> trackers0 = metaRetentionTrackerDao.getTrackers();

        assertThat(trackers0)
                .isEmpty();

        final DataRetentionTracker tracker1 = new DataRetentionTracker(
                "1234", "1 Month", Instant.now());
        final DataRetentionTracker tracker2 = new DataRetentionTracker(
                "2345", "2 Years", Instant.now());
        final DataRetentionTracker tracker3 = new DataRetentionTracker(
                "3456", DataRetentionRule.FOREVER, Instant.now());

        // Insert 3
        metaRetentionTrackerDao.createOrUpdate(tracker1);
        metaRetentionTrackerDao.createOrUpdate(tracker2);
        metaRetentionTrackerDao.createOrUpdate(tracker3);

        final List<DataRetentionTracker> trackers1 = metaRetentionTrackerDao.getTrackers();

        assertThat(trackers1)
                .isNotEmpty();
        assertThat(trackers1)
                .containsExactlyInAnyOrder(tracker1, tracker2, tracker3);

        final DataRetentionTracker tracker1_2 = tracker1.copy(Instant.now());
        final DataRetentionTracker tracker2_2 = tracker2.copy(Instant.now());

        assertThat(tracker1_2)
                .isNotEqualTo(tracker1);
        assertThat(tracker2_2)
                .isNotEqualTo(tracker2);

        metaRetentionTrackerDao.createOrUpdate(tracker1_2);
        metaRetentionTrackerDao.createOrUpdate(tracker2_2);

        final List<DataRetentionTracker> trackers2 = metaRetentionTrackerDao.getTrackers();

        assertThat(trackers2)
                .isNotEmpty();
        assertThat(trackers2)
                .containsExactlyInAnyOrder(tracker1_2, tracker2_2, tracker3);
    }

    @Test
    void testDeleteTrackers() {

        final DataRetentionTracker rule1Tracker1 = new DataRetentionTracker(
                "1234", "1 Month", Instant.now());
        final DataRetentionTracker rule1Tracker2 = new DataRetentionTracker(
                "1234", "2 Years", Instant.now());

        final DataRetentionTracker rule2Tracker1 = new DataRetentionTracker(
                "5678", "1 Month", Instant.now());
        final DataRetentionTracker rule2Tracker2 = new DataRetentionTracker(
                "5678", "2 Years", Instant.now());

        metaRetentionTrackerDao.createOrUpdate(rule1Tracker1);
        metaRetentionTrackerDao.createOrUpdate(rule1Tracker2);
        metaRetentionTrackerDao.createOrUpdate(rule2Tracker1);
        metaRetentionTrackerDao.createOrUpdate(rule2Tracker2);

        final List<DataRetentionTracker> trackers1 = metaRetentionTrackerDao.getTrackers();

        assertThat(trackers1)
                .isNotEmpty();
        assertThat(trackers1)
                .containsExactlyInAnyOrder(rule1Tracker1, rule1Tracker2, rule2Tracker1, rule2Tracker2);

        metaRetentionTrackerDao.deleteTrackers(rule1Tracker1.getRulesVersion());

        final List<DataRetentionTracker> trackers2 = metaRetentionTrackerDao.getTrackers();

        assertThat(trackers2)
                .isNotEmpty();
        assertThat(trackers2)
                .containsExactlyInAnyOrder(rule2Tracker1, rule2Tracker2);
    }
}
