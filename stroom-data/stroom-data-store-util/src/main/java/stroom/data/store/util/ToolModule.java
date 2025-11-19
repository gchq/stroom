/*
 * Copyright 2018 Crown Copyright
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

package stroom.data.store.util;

import stroom.cluster.lock.mock.MockClusterLockModule;
import stroom.collection.mock.MockCollectionModule;
import stroom.data.retention.api.DataRetentionRulesProvider;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.dictionary.mock.MockWordListProviderModule;
import stroom.docrefinfo.mock.MockDocRefInfoModule;
import stroom.node.mock.MockNodeServiceModule;
import stroom.security.mock.MockSecurityContextModule;
import stroom.statistics.mock.MockInternalStatisticsModule;
import stroom.task.mock.MockTaskModule;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.io.DirProvidersModule;
import stroom.util.io.PathConfig;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.io.StroomPathConfig;
import stroom.util.metrics.Metrics;
import stroom.util.metrics.MetricsImpl;
import stroom.util.servlet.MockServletModule;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class ToolModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new MockClusterLockModule());
        install(new MockCollectionModule());
        install(new MockDocRefInfoModule());
        install(new MockInternalStatisticsModule());
        install(new MockNodeServiceModule());
        install(new MockSecurityContextModule());
        install(new MockServletModule());
        install(new MockTaskModule());
        install(new MockWordListProviderModule());
        install(new stroom.activity.mock.MockActivityModule());
        install(new stroom.cache.impl.CacheModule());
        install(new stroom.data.store.impl.fs.FsDataStoreModule());
        install(new stroom.data.store.impl.fs.db.FsDataStoreDaoModule());
        install(new stroom.data.store.impl.fs.db.FsDataStoreDaoModule());
        install(new stroom.data.store.impl.fs.db.FsDataStoreDbModule());
        install(new stroom.event.logging.impl.EventLoggingModule());
        install(new stroom.meta.impl.MetaModule());
        install(new stroom.meta.impl.db.MetaDaoModule());
        install(new stroom.meta.impl.db.MetaDbModule());

        bind(PathCreator.class).to(SimplePathCreator.class);
        bind(PathConfig.class).to(StroomPathConfig.class);
        bind(Metrics.class).toInstance(new MetricsImpl(new MetricRegistry()));
        bind(DataRetentionRulesProvider.class).toInstance(() -> null);
        install(new DirProvidersModule());
    }

    @Provides
    EntityEventBus entityEventBus() {
        return event -> {
        };
    }
}
