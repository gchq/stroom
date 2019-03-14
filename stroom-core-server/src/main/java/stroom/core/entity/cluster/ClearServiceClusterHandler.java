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

package stroom.core.entity.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.shared.Clearable;
import stroom.security.Security;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;
import java.util.Optional;
import java.util.Set;


class ClearServiceClusterHandler extends AbstractTaskHandler<ClearServiceClusterTask, VoidResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClearServiceClusterHandler.class);

    private final Set<Clearable> clearables;
    private final Security security;

    @Inject
    ClearServiceClusterHandler(final Set<Clearable> clearables,
                               final Security security) {
        this.clearables = clearables;
        this.security = security;
    }

    @Override
    public VoidResult exec(final ClearServiceClusterTask task) {
        return security.secureResult(() -> {
            if (task == null) {
                throw new RuntimeException("No task supplied");
            }
            if (task.getBeanClass() == null) {
                clearables.forEach(clearable -> {
                    LOGGER.info("Calling clear on {}", clearable);
                    clearable.clear();
                });
            } else {
                LOGGER.info("Calling clear on {}", task.getBeanClass());
                Optional<Clearable> optional = clearables.stream()
                        .filter(clearable -> task.getBeanClass().isAssignableFrom(clearable.getClass()))
                        .findAny();

                if (!optional.isPresent()) {
                    throw new RuntimeException("Cannot find bean of class type: " + task.getBeanClass());
                }

                optional.ifPresent(Clearable::clear);
            }
            return new VoidResult();
        });
    }
}
