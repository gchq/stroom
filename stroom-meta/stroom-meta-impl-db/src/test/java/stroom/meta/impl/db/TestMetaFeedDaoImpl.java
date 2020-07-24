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
import stroom.dictionary.mock.MockWordListProviderModule;
import stroom.docrefinfo.mock.MockDocRefInfoModule;
import stroom.security.mock.MockSecurityContextModule;
import stroom.test.common.util.db.DbTestModule;

import com.google.inject.Guice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestMetaFeedDaoImpl {
    @Inject
    private Cleanup cleanup;
    @Inject
    private MetaFeedDaoImpl feedDao;

    @BeforeEach
    void setup() {
        Guice.createInjector(
                new MetaTestModule(),
                new MetaDbModule(),
                new MockClusterLockModule(),
                new MockSecurityContextModule(),
                new MockCollectionModule(),
                new MockDocRefInfoModule(),
                new MockWordListProviderModule(),
                new CacheModule(),
                new DbTestModule())
                .injectMembers(this);
        // Delete everything
        cleanup.clear();
    }

    @Test
    void test() {
        String feedName = "TEST";
        Integer id1 = feedDao.getOrCreate(feedName);
        Integer id2 = feedDao.getOrCreate(feedName);

        assertThat(id1).isEqualTo(id2);

        feedName = "TEST2";
        id1 = feedDao.getOrCreate(feedName);
        id2 = feedDao.getOrCreate(feedName);

        assertThat(id1).isEqualTo(id2);

        assertThat(feedDao.list().size()).isEqualTo(2);
    }

    @Test
    void testDuplicateCreate() {
        String feedName = "TEST";
        Optional<Integer> id1 = feedDao.create(feedName);
        Optional<Integer> id2 = feedDao.create(feedName);

        assertThat(id1).isNotEmpty();
        assertThat(id2).isEmpty();

        id2 = Optional.ofNullable(feedDao.getOrCreate(feedName));
        assertThat(id1).isEqualTo(id2);
    }
}
