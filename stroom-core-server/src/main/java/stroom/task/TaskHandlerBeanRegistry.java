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
import stroom.util.shared.SharedObject;
import stroom.util.shared.Task;
import stroom.util.spring.StroomBeanStore;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TaskHandlerBeanRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskHandlerBeanRegistry.class);

    private final StroomBeanStore beanStore;
    private volatile Handlers handlers;

    @Inject
    TaskHandlerBeanRegistry(final StroomBeanStore beanStore) {
        this.beanStore = beanStore;
    }

    @SuppressWarnings("unchecked")
    public <R, H extends TaskHandler<Task<R>, R>> H findHandler(final Task<R> task) {
        final String handlerName = getHandlers().getHandlerMap().get(task.getClass());

        if (handlerName == null) {
            throw new RuntimeException("No handler for " + task.getClass().getName());
        }

        return (H) beanStore.getBean(handlerName);
    }

    public TaskHandlerBean getTaskHandlerBean(final Task<? extends SharedObject> task) {
        return getHandlers().getTaskHandlerMap().get(task.getClass());
    }

    private Handlers getHandlers() {
        if (handlers == null) {
            synchronized (this) {
                if (handlers == null) {
                    handlers = new Handlers(beanStore);
                }
            }
        }
        return handlers;
    }

    private static class Handlers {
        private final Map<Class<?>, String> handlerMap = new HashMap<>();
        private final Map<Class<?>, TaskHandlerBean> taskHandlerMap = new HashMap<>();

        Handlers(final StroomBeanStore beanStore) {
            final Set<String> beanList = beanStore.getAnnotatedStroomBeans(TaskHandlerBean.class);

            for (final String handlerName : beanList) {
                final TaskHandlerBean handlerBean = beanStore.findAnnotationOnBean(handlerName, TaskHandlerBean.class);
                final Class<?> taskClass = handlerBean.task();

                final Object previousHandler = handlerMap.put(taskClass, handlerName);

                taskHandlerMap.put(taskClass, handlerBean);

                // Check that there isn't a handler already associated
                // with the task.
                if (previousHandler != null) {
                    throw new RuntimeException("TaskHandler \"" + previousHandler + "\" has already been registered for \""
                            + taskClass + "\"");
                }

                LOGGER.debug("postProcessAfterInitialization() - registering {} for action {}", handlerName,
                        taskClass.getSimpleName());
            }
        }

        Map<Class<?>, String> getHandlerMap() {
            return handlerMap;
        }

        Map<Class<?>, TaskHandlerBean> getTaskHandlerMap() {
            return taskHandlerMap;
        }
    }
}
