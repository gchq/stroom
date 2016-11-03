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

package stroom.task.server;

import stroom.util.logging.StroomLogger;
import stroom.util.shared.SharedObject;
import stroom.util.shared.Task;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.ApplicationContextUtil;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TaskHandlerBeanRegistry implements ApplicationContextAware, InitializingBean {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(TaskHandlerBeanRegistry.class);

    @Resource
    private StroomBeanStore beanStore;

    private Map<Class<?>, String> handlerMap = new HashMap<>();
    private Map<Class<?>, TaskHandlerBean> taskHandlerMap = new HashMap<>();
    private ApplicationContext applicationContext;

    @SuppressWarnings("unchecked")
    public <R, H extends TaskHandler<Task<R>, R>> H findHandler(final Task<R> task) {
        final String handlerName = handlerMap.get(task.getClass());

        if (handlerName == null) {
            throw new RuntimeException("No handler for " + task.getClass().getName());
        }

        return (H) beanStore.getBean(handlerName);
    }

    public TaskHandlerBean getTaskHandlerBean(final Task<? extends SharedObject> task) {
        return taskHandlerMap.get(task.getClass());
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        final List<String> beanList = ApplicationContextUtil.getBeanNamesWithAnnotation(applicationContext,
                TaskHandlerBean.class);

        for (final String handlerName : beanList) {
            Class<?> handlerClass = applicationContext.getType(handlerName);

            final TaskHandlerBean handlerBean = handlerClass.getAnnotation(TaskHandlerBean.class);
            final Class<?> taskClass = handlerBean.task();

            final Object previousHandler = handlerMap.put(taskClass, handlerName);

            taskHandlerMap.put(taskClass, handlerBean);

            // Check that there isn't a handler already associated
            // with the task.
            if (previousHandler != null) {
                throw new RuntimeException("TaskHandler \"" + previousHandler + "\" has already been registered for \""
                        + taskClass + "\"");
            }

            LOGGER.debug("postProcessAfterInitialization() - registering %s for action %s", handlerName,
                    taskClass.getSimpleName());
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
