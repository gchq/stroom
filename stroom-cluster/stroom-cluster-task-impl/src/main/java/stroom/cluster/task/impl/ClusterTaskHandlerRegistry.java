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

package stroom.cluster.task.impl;

import stroom.cluster.task.api.ClusterTask;
import stroom.cluster.task.api.ClusterTaskHandler;
import stroom.cluster.task.api.ClusterTaskType;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.Map;

@Singleton
public class ClusterTaskHandlerRegistry {
    private final Map<ClusterTaskType, Provider<ClusterTaskHandler>> taskHandlerMap;

    @Inject
    ClusterTaskHandlerRegistry(final Map<ClusterTaskType, Provider<ClusterTaskHandler>> taskHandlerMap) {
        this.taskHandlerMap = taskHandlerMap;
    }

    @SuppressWarnings("unchecked")
    public <R, H extends ClusterTaskHandler<ClusterTask<R>, R>> H findHandler(final ClusterTask<R> task) {
        final Provider<ClusterTaskHandler> taskHandlerProvider = taskHandlerMap.get(new ClusterTaskType(task.getClass()));
        if (taskHandlerProvider == null) {
            throw new RuntimeException("No handler for " + task.getClass().getName());
        }
        return (H) taskHandlerProvider.get();
    }
}
