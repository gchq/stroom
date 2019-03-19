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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import stroom.entity.shared.EntityAction;
import stroom.entity.shared.EntityEvent;
import stroom.entity.shared.EntityEvent.Handler;
import stroom.entity.shared.EntityEventBus;

public class ToolModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new stroom.activity.impl.mock.MockActivityModule());
        install(new stroom.cache.impl.CacheModule());
        install(new stroom.cluster.lock.impl.mock.MockClusterLockModule());
        install(new stroom.data.store.impl.fs.FsDataStoreModule());
        install(new stroom.event.logging.impl.EventLoggingModule());
        install(new stroom.meta.impl.db.MetaDbModule());
        install(new stroom.security.impl.mock.MockSecurityContextModule());
        install(new stroom.statistics.impl.mock.MockInternalStatisticsModule());
    }

    @Provides
    EntityEventBus entityEventBus() {
        return new EntityEventBus() {
            @Override
            public void addHandler(final Handler handler, final String type, final EntityAction... action) {
            }

            @Override
            public void fire(final EntityEvent event) {
            }
        };
    }
}