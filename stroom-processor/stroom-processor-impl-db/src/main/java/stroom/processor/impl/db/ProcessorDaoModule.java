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

package stroom.processor.impl.db;

import stroom.processor.impl.ProcessorDao;
import stroom.processor.impl.ProcessorFilterDao;
import stroom.processor.impl.ProcessorFilterTrackerDao;
import stroom.processor.impl.ProcessorTaskDao;
import stroom.processor.impl.ProcessorTaskDeleteExecutor;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

public class ProcessorDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(ProcessorDao.class).to(ProcessorDaoImpl.class);
        bind(ProcessorFilterDao.class).to(ProcessorFilterDaoImpl.class);
        bind(ProcessorTaskDao.class).to(ProcessorTaskDaoImpl.class);
        bind(ProcessorTaskDeleteExecutor.class).to(ProcessorTaskDeleteExecutorImpl.class);
        bind(ProcessorFilterTrackerDao.class).to(ProcessorFilterTrackerDaoImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(ProcessorNodeCache.class)
                .addBinding(ProcessorFeedCache.class);
    }
}
