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
import stroom.entity.shared.Clearable;
import stroom.guice.StroomBeanStore;
import stroom.security.Security;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskHandlerBean;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;
import java.util.Set;

@TaskHandlerBean(task = ClearServiceClusterTask.class)
class ClearServiceClusterHandler extends AbstractTaskHandler<ClearServiceClusterTask, VoidResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClearServiceClusterHandler.class);

    private final StroomBeanStore stroomBeanStore;
    private final Security security;

    @Inject
    ClearServiceClusterHandler(final StroomBeanStore stroomBeanStore,
                               final Security security) {
        this.stroomBeanStore = stroomBeanStore;
        this.security = security;
    }

    @Override
    public VoidResult exec(final ClearServiceClusterTask task) {
        return security.secureResult(() -> {
            if (task == null) {
                throw new RuntimeException("No task supplied");
            }
            if (task.getBeanClass() == null) {
                final Set<Clearable> set = stroomBeanStore.getInstancesOfType(Clearable.class);
                set.forEach(clearable -> {
                    LOGGER.info("Calling clear on {}", clearable);
                    clearable.clear();
                });
            } else {
                LOGGER.info("Calling clear on {}", task.getBeanClass());
                final Object obj = stroomBeanStore.getInstance(task.getBeanClass());
                if (obj == null) {
                    throw new RuntimeException("Cannot find bean of class type: " + task.getBeanClass());
                }

                ((Clearable) obj).clear();
            }
            return new VoidResult();
        });
    }
}
