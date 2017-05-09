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
import org.springframework.context.annotation.Scope;
import stroom.entity.shared.FindClearService;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.VoidResult;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@TaskHandlerBean(task = FindClearServiceClusterTask.class)
@Scope(value = StroomScope.TASK)
public class FindClearServiceClusterHandler extends AbstractTaskHandler<FindClearServiceClusterTask<?>, VoidResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FindClearServiceClusterHandler.class);

    private final StroomBeanStore stroomBeanStore;

    @Inject
    FindClearServiceClusterHandler(final StroomBeanStore stroomBeanStore) {
        this.stroomBeanStore = stroomBeanStore;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public VoidResult exec(final FindClearServiceClusterTask<?> task) {
        try {
            if (task == null) {
                throw new RuntimeException("No task supplied");
            }

            if (task.getBeanClass() == null) {
                throw new RuntimeException("No task bean class supplied");
            }

            final Object obj = stroomBeanStore.getBean(task.getBeanClass());
            if (obj == null) {
                throw new RuntimeException("Cannot find bean of class type: " + task.getBeanClass());
            }

            ((FindClearService) obj).findClear(task.getCriteria());

        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        return new VoidResult();
    }
}
