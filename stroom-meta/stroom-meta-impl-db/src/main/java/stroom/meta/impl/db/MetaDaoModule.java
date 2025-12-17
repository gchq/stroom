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

import stroom.cache.api.CacheManager;
import stroom.lifecycle.api.LifecycleBinder;
import stroom.meta.impl.MetaDao;
import stroom.meta.impl.MetaFeedDao;
import stroom.meta.impl.MetaKeyDao;
import stroom.meta.impl.MetaProcessorDao;
import stroom.meta.impl.MetaRetentionTrackerDao;
import stroom.meta.impl.MetaTypeDao;
import stroom.meta.impl.MetaValueDao;
import stroom.util.RunnableWrapper;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;

public class MetaDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        requireBinding(CacheManager.class);

        bind(MetaFeedDao.class).to(MetaFeedDaoImpl.class);
        bind(MetaTypeDao.class).to(MetaTypeDaoImpl.class);
        bind(MetaProcessorDao.class).to(MetaProcessorDaoImpl.class);
        bind(MetaKeyDao.class).to(MetaKeyDaoImpl.class);
        bind(MetaValueDao.class).to(MetaValueDaoImpl.class);
        bind(MetaDao.class).to(MetaDaoImpl.class);
        bind(MetaRetentionTrackerDao.class).to(MetaRetentionTrackerDaoImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(MetaValueDaoImpl.class)
                .addBinding(MetaKeyDaoImpl.class)
                .addBinding(MetaProcessorDaoImpl.class)
                .addBinding(MetaTypeDaoImpl.class)
                .addBinding(MetaFeedDaoImpl.class);

        LifecycleBinder.create(binder())
                .bindShutdownTaskTo(MetaValueServiceFlush.class);


    }

    private static class MetaValueServiceFlush extends RunnableWrapper {

        @Inject
        MetaValueServiceFlush(final MetaValueDaoImpl metaValueService) {
            super(metaValueService::flush);
        }
    }
}
