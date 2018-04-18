/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.entity.cluster;

import stroom.entity.FindDeleteService;
import stroom.guice.StroomBeanStore;
import stroom.security.Security;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;

@TaskHandlerBean(task = FindDeleteServiceClusterTask.class)
class FindDeleteServiceClusterHandler extends AbstractTaskHandler<FindDeleteServiceClusterTask<?>, VoidResult> {
    private final StroomBeanStore stroomBeanStore;
    private final Security security;

    @Inject
    FindDeleteServiceClusterHandler(final StroomBeanStore stroomBeanStore,
                                    final Security security) {
        this.stroomBeanStore = stroomBeanStore;
        this.security = security;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public VoidResult exec(final FindDeleteServiceClusterTask<?> task) {
        return security.secureResult(() -> {
            if (task == null) {
                throw new RuntimeException("No task supplied");
            }
            if (task.getBeanClass() == null) {
                throw new RuntimeException("No task bean class supplied");
            }

            final Object obj = stroomBeanStore.getInstance(task.getBeanClass());
            if (obj == null) {
                throw new RuntimeException("Cannot find bean of class type: " + task.getBeanClass());
            }

            ((FindDeleteService) obj).findDelete(task.getCriteria());
            return new VoidResult();
        });
    }
}
