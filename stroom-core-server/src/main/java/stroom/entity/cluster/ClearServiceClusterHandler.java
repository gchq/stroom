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

import stroom.entity.shared.Clearable;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.VoidResult;
import stroom.util.spring.StroomBeanStore;

import javax.inject.Inject;

@TaskHandlerBean(task = ClearServiceClusterTask.class)
class ClearServiceClusterHandler extends AbstractTaskHandler<ClearServiceClusterTask, VoidResult> {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(ClearServiceClusterHandler.class);

    private final StroomBeanStore stroomBeanStore;

    @Inject
    ClearServiceClusterHandler(final StroomBeanStore stroomBeanStore) {
        this.stroomBeanStore = stroomBeanStore;
    }

    @Override
    public VoidResult exec(final ClearServiceClusterTask task) {
        if (task == null) {
            throw new RuntimeException("No task supplied");
        }
        if (task.getBeanClass() == null) {
            for (String bean : stroomBeanStore.getStroomBeanByType(Clearable.class)) {
                LOGGER.info("Calling clear on %s", bean);
                ((Clearable) stroomBeanStore.getBean(bean)).clear();
            }
        } else {
            LOGGER.info("Calling clear on %s", task.getBeanClass());
            final Object obj = stroomBeanStore.getBean(task.getBeanClass());
            if (obj == null) {
                throw new RuntimeException("Cannot find bean of class type: " + task.getBeanClass());
            }

            ((Clearable) obj).clear();
        }
        return new VoidResult();
    }
}
