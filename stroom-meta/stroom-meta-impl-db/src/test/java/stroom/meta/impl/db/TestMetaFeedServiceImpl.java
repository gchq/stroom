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

import com.google.inject.Guice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import stroom.cluster.lock.impl.mock.MockClusterLockModule;
import stroom.meta.impl.db.MetaDbModule;
import stroom.meta.impl.db.MetaFeedServiceImpl;
import stroom.meta.impl.db.MetaServiceImpl;
import stroom.security.impl.mock.MockSecurityContextModule;

import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

class TestMetaFeedServiceImpl {
    @Inject
    private MetaServiceImpl dataMetaService;
    @Inject
    private MetaFeedServiceImpl feedService;

    @BeforeEach
    void setup() {
        Guice.createInjector(new MetaDbModule(), new MockClusterLockModule(), new MockSecurityContextModule()).injectMembers(this);
    }

    @Test
    void test() {
        // Delete everything.
        dataMetaService.deleteAll();
        feedService.deleteAll();

        String feedName = "TEST";
        Integer id1 = feedService.getOrCreate(feedName);
        Integer id2 = feedService.getOrCreate(feedName);

        assertThat(id1).isEqualTo(id2);

        feedName = "TEST2";
        id1 = feedService.getOrCreate(feedName);
        id2 = feedService.getOrCreate(feedName);

        assertThat(id1).isEqualTo(id2);

        assertThat(feedService.list().size()).isEqualTo(2);
    }

    @Test
    void testDuplicateCreate() {
        // Delete everything.
        dataMetaService.deleteAll();
        feedService.deleteAll();

        String feedName = "TEST";
        Integer id1 = feedService.create(feedName);
        Integer id2 = feedService.create(feedName);

        assertThat(id1).isEqualTo(id2);
    }
}
