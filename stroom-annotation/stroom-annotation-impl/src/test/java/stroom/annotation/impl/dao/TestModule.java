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

package stroom.annotation.impl.dao;

import stroom.annotation.impl.db.AnnotationDbModule;
import stroom.cache.impl.CacheModule;
import stroom.collection.mock.MockCollectionModule;
import stroom.dictionary.mock.MockWordListProviderModule;
import stroom.docstore.mock.MockDocFinderModule;
import stroom.meta.api.StreamFeedProvider;
import stroom.node.mock.MockNodeServiceModule;
import stroom.security.mock.MockSecurityContextModule;
import stroom.security.user.api.UserRefLookup;
import stroom.task.mock.MockTaskModule;
import stroom.test.common.MockMetrics;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.metrics.Metrics;
import stroom.util.shared.UserRef;

import com.google.inject.AbstractModule;

import java.util.Optional;

public class TestModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();
        install(new AnnotationDaoModule());
        install(new AnnotationDbModule());
        install(new MockCollectionModule());
        install(new MockSecurityContextModule());
        install(new MockWordListProviderModule());
        install(new DbTestModule());
        install(new MockTaskModule());
        install(new CacheModule());
        install(new MockNodeServiceModule());
        install(new MockDocFinderModule());

        bind(UserRefLookup.class).toInstance((userUuid, context) ->
                Optional.of(UserRef.forUserUuid(userUuid)));
        bind(StreamFeedProvider.class).toInstance(id -> "TEST_FEED_NAME");
        bind(Metrics.class).toInstance(new MockMetrics());
        bind(EntityEventBus.class).toInstance(EntityEventBus.NO_OP_EVENT_BUS);
    }
}
