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

package stroom.util.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.CustomScopeConfigurer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomScope;

import java.util.HashMap;
import java.util.Map;

/**
 * A package-private context configuration for a specific test.
 */
@Configuration
class TaskScopeTestConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskScopeTestConfiguration.class);

    public TaskScopeTestConfiguration() {
        LOGGER.info("TaskScopeConfiguration loading...");
    }

//    @Bean
//    public StroomBeanStore stroomBeanStore(final ApplicationContext applicationContext, final BeanFactory beanFactory) {
//        return new StroomBeanStore(applicationContext, beanFactory);
//    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public TaskScopeTestObject1 taskScopeTestObject1() {
        return new TaskScopeTestObject1();
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public TaskScopeTestObject2 taskScopeTestObject2() {
        return new TaskScopeTestObject2();
    }

    @Bean
    @Scope(value = StroomScope.SINGLETON)
    public TaskScopeTestSingleton taskScopeTestSingleton() {
        return new TaskScopeTestSingleton();
    }

    @Bean
    public CustomScopeConfigurer customScopeConfigurer() {
        CustomScopeConfigurer customScopeConfigurer = new CustomScopeConfigurer();
        Map<String, Object> scopes = new HashMap<>();
        scopes.put("task", "stroom.util.task.TaskScope");
        customScopeConfigurer.setScopes(scopes);
        return customScopeConfigurer;
    }
}
