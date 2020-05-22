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

package stroom.meta.impl.db;

import stroom.cache.impl.CacheModule;
import stroom.cluster.lock.mock.MockClusterLockModule;
import stroom.collection.mock.MockCollectionModule;
import stroom.data.retention.shared.DataRetentionTracker;
import stroom.dictionary.mock.MockWordListProviderModule;
import stroom.security.mock.MockSecurityContextModule;
import stroom.test.common.util.db.DbTestModule;

import com.google.inject.Guice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.time.Instant;
import java.util.Optional;

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
            new MockClusterLockModule(),
            new MockSecurityContextModule(),
            new MockCollectionModule(),
            new MockWordListProviderModule(),
            new CacheModule(),
            new DbTestModule())
            .injectMembers(this);

        // Delete everything
        cleanup.clear();
    }

    @Test
    void testGet_doesntExist() {

        final Optional<DataRetentionTracker> optTracker = metaRetentionTrackerDao.getTracker();

        assertThat(optTracker).isEmpty();
    }

    @Test
    void testClear() {

        final DataRetentionTracker tracker1 = new DataRetentionTracker(Instant.now(), "1234");
        metaRetentionTrackerDao.createOrUpdate(tracker1);

        final Optional<DataRetentionTracker> optTracker = metaRetentionTrackerDao.getTracker();

        assertThat(optTracker).isPresent();

        metaRetentionTrackerDao.clear();

        final Optional<DataRetentionTracker> optTracker2 = metaRetentionTrackerDao.getTracker();

        assertThat(optTracker2).isEmpty();
    }

    @Test
    void testCreateThenUpdate() {

        final DataRetentionTracker tracker1 = new DataRetentionTracker(Instant.now(), "1234");
        final DataRetentionTracker tracker2 = new DataRetentionTracker(Instant.now(), "5678");

        metaRetentionTrackerDao.createOrUpdate(tracker1);

        final Optional<DataRetentionTracker> optTracker1 = metaRetentionTrackerDao.getTracker();

        assertThat(optTracker1).isPresent();
        optTracker1.ifPresent(tracker ->
                assertThat(tracker).isEqualTo(tracker1));

        metaRetentionTrackerDao.createOrUpdate(tracker2);

        final Optional<DataRetentionTracker> optTracker2 = metaRetentionTrackerDao.getTracker();

        assertThat(optTracker2).isPresent();
        optTracker2.ifPresent(tracker ->
                assertThat(tracker).isEqualTo(tracker2));

        assertThat(optTracker1.get())
                .isNotEqualTo(optTracker2.get());
    }
}
