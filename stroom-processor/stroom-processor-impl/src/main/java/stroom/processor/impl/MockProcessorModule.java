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

package stroom.processor.impl;

import com.google.inject.AbstractModule;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorService;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

public class MockProcessorModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ProcessorTaskManager.class).to(MockProcessorTaskManager.class);
        bind(ProcessorDao.class).to(MockProcessorDao.class);
        bind(ProcessorService.class).to(ProcessorServiceImpl.class);
        bind(ProcessorFilterDao.class).to(MockProcessorFilterDao.class);
        bind(ProcessorTaskDao.class).to(MockProcessorTaskDao.class);
        bind(ProcessorFilterService.class).to(ProcessorFilterServiceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(MockProcessorDao.class)
                .addBinding(MockProcessorFilterDao.class)
                .addBinding(MockProcessorTaskDao.class)
                .addBinding(ProcessorCache.class)
                .addBinding(ProcessorFilterCache.class);
    }
}