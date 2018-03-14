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

package stroom.jobsystem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.guice.StroomBeanStore;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Singleton
class DistributedTaskFactoryBeanRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedTaskFactoryBeanRegistry.class);
    private final Map<String, DistributedTaskFactory> factoryMap = new HashMap<>();

    @Inject
    DistributedTaskFactoryBeanRegistry(final StroomBeanStore stroomBeanStore) {
        Set<DistributedTaskFactory> distributedTaskFactories = stroomBeanStore.getBeansOfType(DistributedTaskFactory.class);
        for (final DistributedTaskFactory distributedTaskFactory : distributedTaskFactories) {
            DistributedTaskFactoryBean annotation = distributedTaskFactory.getClass().getAnnotation(DistributedTaskFactoryBean.class);
            final String jobName = annotation.jobName();

            final Object previousFactory = factoryMap.put(jobName, distributedTaskFactory);

            // Check that there isn't a factory already associated with the job.
            if (previousFactory != null) {
                throw new RuntimeException(
                        "TaskFactory \"" + previousFactory + "\" has already been registered for \"" + jobName + "\"");
            }

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("postProcessAfterInitialization() - registering task factory " + distributedTaskFactory + " for job "
                        + jobName);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public DistributedTaskFactory<DistributedTask<?>, ?> findFactory(final String jobName) {
        final DistributedTaskFactory distributedTaskFactory = factoryMap.get(jobName);

        if (distributedTaskFactory == null) {
            throw new RuntimeException("No factory for " + jobName);
        }

        return distributedTaskFactory;
    }

    Map<String, DistributedTaskFactory> getFactoryMap() {
        return factoryMap;
    }
}
