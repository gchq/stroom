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
import stroom.job.api.DistributedTaskFactory;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorService;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.ProcessorResource;
import stroom.processor.shared.ProcessorTaskResource;
import stroom.searchable.api.Searchable;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.RestResource;

public class ProcessorModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ProcessorTaskManager.class).to(ProcessorTaskManagerImpl.class);
        bind(ProcessorFilterService.class).to(ProcessorFilterServiceImpl.class);
        bind(ProcessorService.class).to(ProcessorServiceImpl.class);
        bind(ProcessorResource.class).to(ProcessorResourceImpl.class);
        bind(ProcessorTaskResource.class).to(ProcessorTaskResourceImpl.class);
        bind(ProcessorTaskService.class).to(ProcessorTaskServiceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), RestResource.class)
                .addBinding(ProcessorResourceImpl.class)
                .addBinding(ProcessorFilterResourceImpl.class)
                .addBinding(ProcessorTaskResourceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), DistributedTaskFactory.class)
                .addBinding(DataProcessorTaskFactory.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(ProcessorCache.class)
                .addBinding(ProcessorFilterCache.class);

        GuiceUtil.buildMultiBinder(binder(), RestResource.class)
                .addBinding(StreamTaskResource.class);

        GuiceUtil.buildMultiBinder(binder(), Searchable.class)
                .addBinding(ProcessorTaskServiceImpl.class);
    }
}