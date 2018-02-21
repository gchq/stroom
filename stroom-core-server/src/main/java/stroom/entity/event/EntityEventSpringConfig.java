/*
 * Copyright 2018 Crown Copyright
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

package stroom.entity.event;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.task.cluster.TargetNodeSetFactory;
import stroom.task.TaskManager;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomScope;

@Configuration
public class EntityEventSpringConfig {
    @Bean
    @Scope(StroomScope.TASK)
    public ClusterEntityEventTaskHandler clusterEntityEventTaskHandler(final EntityEventBusImpl entityEventBusImpl) {
        return new ClusterEntityEventTaskHandler(entityEventBusImpl);
    }

    @Bean
    @Scope(StroomScope.TASK)
    public DispatchEntityEventTaskHandler dispatchEntityEventTaskHandler(final ClusterDispatchAsyncHelper dispatchHelper,
                                                                         final TargetNodeSetFactory targetNodeSetFactory) {
        return new DispatchEntityEventTaskHandler(dispatchHelper, targetNodeSetFactory);
    }

    @Bean
    public EntityEventBus entityEventBus(final StroomBeanStore stroomBeanStore, final TaskManager taskManager) {
        return new EntityEventBusImpl(stroomBeanStore, taskManager);
    }
}