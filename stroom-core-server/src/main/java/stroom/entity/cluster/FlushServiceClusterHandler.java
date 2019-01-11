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

package stroom.entity.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.Flushable;
import stroom.security.Security;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;
import java.util.Optional;
import java.util.Set;


class FlushServiceClusterHandler extends AbstractTaskHandler<FlushServiceClusterTask, VoidResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FlushServiceClusterHandler.class);

    private final Set<Flushable> flushables;
    private final Security security;

    @Inject
    FlushServiceClusterHandler(final Set<Flushable> flushables,
                               final Security security) {
        this.flushables = flushables;
        this.security = security;
    }

    @Override
    public VoidResult exec(final FlushServiceClusterTask task) {
        return security.secureResult(() -> {
            if (task == null) {
                throw new RuntimeException("No task supplied");
            }
            if (task.getBeanClass() == null) {
                flushables.forEach(flushable -> {
                    LOGGER.info("Calling flush on {}", flushable);
                    flushable.flush();
                });
            } else {
                LOGGER.info("Calling clear on {}", task.getBeanClass());
                Optional<Flushable> optional = flushables.stream()
                        .filter(flushable -> task.getBeanClass().isAssignableFrom(flushable.getClass()))
                        .findAny();

                if (!optional.isPresent()) {
                    throw new RuntimeException("Cannot find bean of class type: " + task.getBeanClass());
                }

                optional.ifPresent(Flushable::flush);
            }
            return new VoidResult();
        });
    }
}
