/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.job.impl;

import stroom.job.api.DistributedTaskFactory;
import stroom.job.api.DistributedTaskFactoryDescription;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Singleton
class DistributedTaskFactoryRegistryImpl implements DistributedTaskFactoryRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedTaskFactoryRegistryImpl.class);
    private final Map<String, DistributedTaskFactory> factoryMap = new HashMap<>();

    @Inject
    DistributedTaskFactoryRegistryImpl(final Set<DistributedTaskFactory> distributedTaskFactories) {
        for (final DistributedTaskFactory distributedTaskFactory : distributedTaskFactories) {
            final DistributedTaskFactoryDescription annotation = distributedTaskFactory.getClass().getAnnotation(
                    DistributedTaskFactoryDescription.class);
            final String jobName = annotation.jobName();

            final Object previousFactory = factoryMap.put(jobName, distributedTaskFactory);

            // Check that there isn't a factory already associated with the job.
            if (previousFactory != null) {
                throw new RuntimeException(
                        "TaskFactory \"" + previousFactory + "\" has already been registered for \"" + jobName + "\"");
            }

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("postProcessAfterInitialization() - registering task factory " +
                        distributedTaskFactory + " for job " + jobName);
            }
        }
    }

    @Override
    public Map<String, DistributedTaskFactory> getFactoryMap() {
        return factoryMap;
    }
}
