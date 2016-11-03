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

import stroom.util.logging.StroomLogger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Package-private context configuration, for use by specific tests.
 */
@Configuration
class StroomBeanLifeCycleTestConfiguration {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(StroomBeanLifeCycleTestConfiguration.class);

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
    public StroomBeanLifeCycle stroomBeanLifeCycle() {
        return new StroomBeanLifeCycle();
    }

    @Bean
    StroomBeanStore stroomBeanStore() {
        return new StroomBeanStore();
    }

    @Bean
    public StroomBeanLifeCycleReloadableContextBeanProcessor beanProcessor(StroomBeanLifeCycle stroomBeanLifeCycle) {
        StroomBeanLifeCycleReloadableContextBeanProcessor beanProcessor = new StroomBeanLifeCycleReloadableContextBeanProcessor();
        beanProcessor.setName("testContext");
        beanProcessor.setStroomBeanLifeCycle(stroomBeanLifeCycle);
        return beanProcessor;
    }
}
