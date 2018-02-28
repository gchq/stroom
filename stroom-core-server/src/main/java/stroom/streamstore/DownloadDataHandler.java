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

package stroom.streamstore;

import stroom.entity.util.EntityServiceExceptionUtil;
import stroom.logging.StreamEventLog;
import stroom.resource.ResourceStore;
import stroom.security.Secured;
import stroom.streamstore.shared.DownloadDataAction;
import stroom.streamstore.shared.Stream;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.task.TaskManager;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.ArrayList;

@TaskHandlerBean(task = DownloadDataAction.class)
@Secured(Stream.EXPORT_DATA_PERMISSION)
class DownloadDataHandler extends AbstractTaskHandler<DownloadDataAction, ResourceGeneration> {
    private final ResourceStore resourceStore;
    private final TaskManager taskManager;
    private final StreamEventLog streamEventLog;

    @Inject
    DownloadDataHandler(final ResourceStore resourceStore,
                        final TaskManager taskManager,
                        final StreamEventLog streamEventLog) {
        this.resourceStore = resourceStore;
        this.taskManager = taskManager;
        this.streamEventLog = streamEventLog;
    }

    @Override
    public ResourceGeneration exec(final DownloadDataAction action) {
        ResourceKey resourceKey;
        try {
            // Import file.
            resourceKey = resourceStore.createTempFile("StroomData.zip");
            final Path file = resourceStore.getTempFile(resourceKey);

            final StreamDownloadSettings settings = new StreamDownloadSettings();
            taskManager.exec(new StreamDownloadTask(action.getUserToken(), action.getCriteria(), file, settings));

            streamEventLog.exportStream(action.getCriteria(), null);

        } catch (final Exception ex) {
            streamEventLog.exportStream(action.getCriteria(), ex);
            throw EntityServiceExceptionUtil.create(ex);
        }
        return new ResourceGeneration(resourceKey, new ArrayList<>());
    }
}
