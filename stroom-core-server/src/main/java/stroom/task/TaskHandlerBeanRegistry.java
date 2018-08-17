/*
 * Copyright 2016 Crown Copyright
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.task.api.TaskHandler;
import stroom.task.api.TaskHandlerBean;
import stroom.task.shared.Task;
import stroom.guice.StroomBeanStore;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Singleton
public class TaskHandlerBeanRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskHandlerBeanRegistry.class);
    private volatile Map<Class<?>, Class<TaskHandler>> taskHandlerMap;

    private final StroomBeanStore beanStore;

    @Inject
    TaskHandlerBeanRegistry(final StroomBeanStore beanStore) {
        this.beanStore = beanStore;
    }

    //    @Inject
//    TaskHandlerBeanRegistry(final Collection<Provider<TaskHandler>> taskHandlerProviders) {
//        taskHandlerProviders.forEach(taskHandlerProvider -> {
//            final TaskHandler taskHandler = taskHandlerProvider.get();
//            if (taskHandler != null) {
//                final TaskHandlerBean taskHandlerBean = taskHandler.getClass().getAnnotation(TaskHandlerBean.class);
//                if (taskHandlerBean != null) {
//                    final Class<?> task = taskHandlerBean.task();
//                    taskHandlerMap.put(task, taskHandlerProvider);
//                }
//            }
//        });
//    }

    @SuppressWarnings("unchecked")
    public <R, H extends TaskHandler<Task<R>, R>> H findHandler(final Task<R> task) {
        if (taskHandlerMap == null) {
            synchronized (this) {
                if (taskHandlerMap == null) {
                    final Map<Class<?>, Class<TaskHandler>> map = new HashMap<>();

                    final Set<TaskHandler> taskHandlers = beanStore.getInstancesOfType(TaskHandler.class);
                    taskHandlers.forEach(taskHandler -> {
                        final Class<TaskHandler> taskHandlerClazz = (Class<TaskHandler>) taskHandler.getClass();
                        final TaskHandlerBean taskHandlerBean = taskHandlerClazz.getAnnotation(TaskHandlerBean.class);
                        if (taskHandlerBean != null) {
                            final Class<?> taskClazz = taskHandlerBean.task();
                            map.put(taskClazz, taskHandlerClazz);
                        }
                    });

                    taskHandlerMap = Collections.unmodifiableMap(map);
                }
            }
        }

        final Class<TaskHandler> taskHandlerClazz = taskHandlerMap.get(task.getClass());
        if (taskHandlerClazz == null) {
            throw new RuntimeException("No handler for " + task.getClass().getName());
        }

        return (H) beanStore.getInstance(taskHandlerClazz);
    }

//    private <T> Collection<Provider<T>> getProviders(Class<T> type) {
//        final TypeLiteral<Collection<Provider<T>>> lit = (TypeLiteral<Collection<Provider<T>>>)TypeLiteral.get(Types.collectionOf(type));
//        final Key<Collection<Provider<T>>> key = Key.get(lit);
//        return this.injector.getInstance(key);
//    }
//
//    private <T> Set<T> getBindings(Class<T> type) {
//        final TypeLiteral<Set<T>> lit = setOf(type);
//        final Key<Set<T>> key = Key.get(lit);
//        return this.injector.getInstance(key);
//    }
//
//    @SuppressWarnings("unchecked")
//    private static <T> TypeLiteral<Set<T>> setOf(Class<T> type) {
//        return (TypeLiteral<Set<T>>)TypeLiteral.get(Types.setOf(type));
//    }
//
//
//    public TaskHandlerBean getTaskHandlerBean(final Task<? extends SharedObject> task) {
//        return getHandlers().getTaskHandlerMap().get(task.getClass());
//    }
//
//    private Handlers getHandlers() {
//        if (handlers == null) {
//            synchronized (this) {
//                if (handlers == null) {
//                    handlers = new Handlers(beanStore);
//                }
//            }
//        }
//        return handlers;
//    }
//
//    private static class Handlers {
//        private final Map<Class<?>, String> handlerMap = new HashMap<>();
//        private final Map<Class<?>, TaskHandlerBean> taskHandlerMap = new HashMap<>();
//
//        Handlers(final StroomBeanStore beanStore) {
//            final Set<String> beanList = beanStore.getAnnotatedClasses(TaskHandlerBean.class);
//
//            for (final String handlerName : beanList) {
//                final TaskHandlerBean handlerBean = beanStore.findAnnotationOnBean(handlerName, TaskHandlerBean.class);
//                final Class<?> taskClass = handlerBean.task();
//
//                final Object previousHandler = handlerMap.put(taskClass, handlerName);
//
//                taskHandlerMap.put(taskClass, handlerBean);
//
//                // Check that there isn't a handler already associated
//                // with the task.
//                if (previousHandler != null) {
//                    throw new RuntimeException("TaskHandler \"" + previousHandler + "\" has already been registered for \""
//                            + taskClass + "\"");
//                }
//
//                LOGGER.debug("postProcessAfterInitialization() - registering {} for action {}", handlerName,
//                        taskClass.getSimpleName());
//            }
//        }
//
//        Map<Class<?>, String> getHandlerMap() {
//            return handlerMap;
//        }
//
//        Map<Class<?>, TaskHandlerBean> getTaskHandlerMap() {
//            return taskHandlerMap;
//        }
//    }
}
