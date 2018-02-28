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

package stroom.util.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Package-private context configuration, for use by specific tests.
 */
@Configuration
class StroomBeanLifeCycleTestConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomBeanLifeCycleTestConfiguration.class);

    public StroomBeanLifeCycleTestConfiguration() {
        LOGGER.info("StroomBeanLifeCycleConfiguration loading...");
    }

    @Bean
    public MockStroomBeanLifeCycleBean bean1() {
        return new MockStroomBeanLifeCycleBean();
    }

    @Bean
    public MockStroomBeanLifeCycleBean bean2() {
        return new MockStroomBeanLifeCycleBean();
    }

    @Bean
    public StroomBeanLifeCycle stroomBeanLifeCycle(final StroomBeanStore stroomBeanStore) {
        return new StroomBeanLifeCycle(stroomBeanStore);
    }

    @Bean
    StroomBeanStore stroomBeanStore(final ApplicationContext applicationContext, final BeanFactory beanFactory) {
        return new StroomBeanStore(applicationContext, beanFactory);
    }

    @Bean
    public StroomBeanLifeCycleReloadableContextBeanProcessor beanProcessor(StroomBeanLifeCycle stroomBeanLifeCycle) {
        StroomBeanLifeCycleReloadableContextBeanProcessor beanProcessor = new StroomBeanLifeCycleReloadableContextBeanProcessor();
        beanProcessor.setName("testContext");
        beanProcessor.setStroomBeanLifeCycle(stroomBeanLifeCycle);
        return beanProcessor;
    }
}
