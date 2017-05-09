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

package stroom.jobsystem.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomStartup;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
public class DistributedTaskFactoryBeanRegistry {
    private Map<String, String> factoryMap = new HashMap<String, String>();
    @Resource
    private StroomBeanStore stroomBeanStore;

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedTaskFactoryBeanRegistry.class);

    @SuppressWarnings("unchecked")
    public DistributedTaskFactory<DistributedTask<?>, ?> findFactory(final String jobName) {
        final String factoryName = factoryMap.get(jobName);

        if (factoryName == null) {
            throw new RuntimeException("No factory for " + jobName);
        }

        return (DistributedTaskFactory<DistributedTask<?>, ?>) stroomBeanStore.getBean(factoryName);
    }

    @StroomStartup
    public void afterPropertiesSet() throws Exception {
        Set<String> beanNames = stroomBeanStore.getStroomBean(DistributedTaskFactoryBean.class);
        for (final String beanName : beanNames) {
            DistributedTaskFactoryBean annotation = stroomBeanStore.findAnnotationOnBean(beanName,
                    DistributedTaskFactoryBean.class);
            final String jobName = annotation.jobName();

            final Object previousFactory = factoryMap.put(jobName, beanName);

            // Check that there isn't a factory already associated with the job.
            if (previousFactory != null) {
                throw new RuntimeException(
                        "TaskFactory \"" + previousFactory + "\" has already been registered for \"" + jobName + "\"");
            }

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("postProcessAfterInitialization() - registering task factory " + beanName + " for job "
                        + jobName);
            }
        }
    }

    public void setTaskFactoryMap(final Map<String, String> taskFactoryMap) {
        this.factoryMap = taskFactoryMap;
    }
}
