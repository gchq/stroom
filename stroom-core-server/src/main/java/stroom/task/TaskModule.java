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

package stroom.task;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.node.NodeCache;
import stroom.security.SecurityContext;
import stroom.servlet.HttpServletRequestHolder;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomScope;
import stroom.task.TaskContext;

import javax.inject.Provider;

public class TaskModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ExecutorProvider.class).to(ExecutorProviderImpl.class);
        bind(TaskManager.class).to(TaskManagerImpl.class);
        bind(TaskContext.class).to(TaskContextImpl.class);

        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.task.FindTaskProgressHandler.class);
        taskHandlerBinder.addBinding().to(stroom.task.FindUserTaskProgressHandler.class);
        taskHandlerBinder.addBinding().to(stroom.task.GenericServerTaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.task.TerminateTaskProgressHandler.class);
    }
    //    @Bean
//    public ExecutorProvider executorProvider(final TaskManager taskManager,
//                                             final SecurityContext securityContext) {
//        return new ExecutorProviderImpl(taskManager, securityContext);
//    }
//
//    @Bean
//    @Scope(StroomScope.TASK)
//    public FindTaskProgressHandler findTaskProgressHandler(final ClusterDispatchAsyncHelper dispatchHelper) {
//        return new FindTaskProgressHandler(dispatchHelper);
//    }
//
//    @Bean
//    @Scope(StroomScope.TASK)
//    public FindUserTaskProgressHandler findUserTaskProgressHandler(final ClusterDispatchAsyncHelper dispatchHelper,
//                                                                   final Provider<HttpServletRequestHolder> httpServletRequestHolderProvider) {
//        HttpServletRequestHolder httpServletRequestHolder = null;
//        try {
//            httpServletRequestHolder = httpServletRequestHolderProvider.get();
//        } catch (final Exception e) {
//            // Ignore
//        }
//
//        return new FindUserTaskProgressHandler(dispatchHelper, httpServletRequestHolder);
//    }
//
//    @Bean
//    @Scope(value = StroomScope.TASK)
//    public GenericServerTaskHandler genericServerTaskHandler(final TaskContext taskContext) {
//        return new GenericServerTaskHandler(taskContext);
//    }
//
//    @Bean
//    public TaskContext taskContext() {
//        return new TaskContextImpl();
//    }
//
//    @Bean
//    public TaskHandlerBeanRegistry taskHandlerBeanRegistry(final StroomBeanStore beanStore) {
//        return new TaskHandlerBeanRegistry(beanStore);
//    }
//
//    @Bean("taskManager")
//    public TaskManager taskManager(final TaskHandlerBeanRegistry taskHandlerBeanRegistry,
//                                   final NodeCache nodeCache,
//                                   final StroomBeanStore beanStore,
//                                   final SecurityContext securityContext) {
//        return new TaskManagerImpl(taskHandlerBeanRegistry, nodeCache, beanStore, securityContext);
//    }
//
//    @Bean("taskContext")
//    @Scope(value = StroomScope.TASK)
//    public TaskMonitorImpl taskContext() {
//        return new SimpleTaskContext();
//    }
//
//    @Bean
//    @Scope(value = StroomScope.TASK)
//    public TerminateTaskProgressHandler terminateTaskProgressHandler(final ClusterDispatchAsyncHelper dispatchHelper) {
//        return new TerminateTaskProgressHandler(dispatchHelper);
//    }
}