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

package stroom.entity.server.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.task.server.TaskCallback;
import stroom.task.server.TaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;

@TaskHandlerBean(task = ClusterEntityEventTask.class)
class ClusterEntityEventTaskHandler implements TaskHandler<ClusterEntityEventTask, VoidResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterEntityEventTaskHandler.class);

    private final EntityEventBusImpl entityEventBusImpl;

    @Inject
    ClusterEntityEventTaskHandler(final EntityEventBusImpl entityEventBusImpl) {
        this.entityEventBusImpl = entityEventBusImpl;
    }

    @Override
    public void exec(final ClusterEntityEventTask task, final TaskCallback<VoidResult> callback) {
        try {
            entityEventBusImpl.fireLocally(task.getEntityEvent());
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        try {
            callback.onSuccess(VoidResult.INSTANCE);
        } catch (final Throwable t) {
            // Ignore errors thrown returning result.
            LOGGER.trace(t.getMessage(), t);
        }
    }
}
