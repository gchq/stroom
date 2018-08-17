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

package stroom.streamtask;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import stroom.entity.CachingEntityManager;
import stroom.entity.FindService;
import stroom.jobsystem.DistributedTaskFactory;
import stroom.persist.EntityManagerSupport;
import stroom.security.Security;
import stroom.task.api.TaskHandler;
import stroom.streamstore.ExpressionToFindCriteria;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.task.TaskHandler;

import javax.inject.Named;

public class StreamTaskModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(StreamTaskCreator.class).to(StreamTaskCreatorImpl.class);
        bind(StreamProcessorFilterService.class).to(StreamProcessorFilterServiceImpl.class);
        bind(StreamProcessorService.class).to(StreamProcessorServiceImpl.class);
        bind(StreamTaskService.class).to(StreamTaskServiceImpl.class);

        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.streamtask.CreateProcessorHandler.class);
        taskHandlerBinder.addBinding().to(stroom.streamtask.CreateStreamTasksTaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.streamtask.FetchProcessorHandler.class);
        taskHandlerBinder.addBinding().to(stroom.streamtask.ReprocessDataHandler.class);
        taskHandlerBinder.addBinding().to(stroom.streamtask.StreamProcessorTaskHandler.class);

        final Multibinder<DistributedTaskFactory> distributedTaskFactoryBinder = Multibinder.newSetBinder(binder(), DistributedTaskFactory.class);
        distributedTaskFactoryBinder.addBinding().to(stroom.streamtask.StreamProcessorTaskFactory.class);

        final Multibinder<FindService> findServiceBinder = Multibinder.newSetBinder(binder(), FindService.class);
        findServiceBinder.addBinding().to(StreamTaskServiceImpl.class);

        final MapBinder<String, Object> entityServiceByTypeBinder = MapBinder.newMapBinder(binder(), String.class, Object.class);
        entityServiceByTypeBinder.addBinding(StreamProcessorFilter.ENTITY_TYPE).to(StreamProcessorFilterServiceImpl.class);
        entityServiceByTypeBinder.addBinding(StreamProcessor.ENTITY_TYPE).to(StreamProcessorServiceImpl.class);
    }

    @Provides
    @Named("cachedStreamProcessorFilterService")
    public StreamProcessorFilterService cachedStreamProcessorFilterService(final CachingEntityManager entityManager,
                                                                           final Security security,
                                                                           final EntityManagerSupport entityManagerSupport,
                                                                           final StreamProcessorService streamProcessorService) {
        return new StreamProcessorFilterServiceImpl(entityManager, security, entityManagerSupport, streamProcessorService);
    }

    @Provides
    @Named("cachedStreamProcessorService")
    public StreamProcessorService cachedStreamProcessorService(final CachingEntityManager entityManager, final Security security) {
        return new StreamProcessorServiceImpl(entityManager, security);
    }
}