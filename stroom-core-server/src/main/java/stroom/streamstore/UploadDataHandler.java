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
import stroom.resource.ResourceStore;
import stroom.security.Secured;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.UploadDataAction;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.task.TaskManager;
import stroom.util.shared.ResourceKey;

import javax.inject.Inject;
import java.nio.file.Path;

@TaskHandlerBean(task = UploadDataAction.class)
@Secured(Stream.IMPORT_DATA_PERMISSION)
public class UploadDataHandler extends AbstractTaskHandler<UploadDataAction, ResourceKey> {
    private final ResourceStore resourceStore;
    private final TaskManager taskManager;

    @Inject
    UploadDataHandler(final ResourceStore resourceStore,
                      final TaskManager taskManager) {
        this.resourceStore = resourceStore;
        this.taskManager = taskManager;
    }

    @Override
    public ResourceKey exec(final UploadDataAction action) {
        try {
            // Import file.
            final Path file = resourceStore.getTempFile(action.getKey());

            taskManager.exec(new StreamUploadTask(action.getUserToken(), action.getFileName(), file,
                    action.getFeed(), action.getStreamType(), action.getEffectiveMs(), action.getMetaData()));

        } catch (final RuntimeException e) {
            throw EntityServiceExceptionUtil.create(e);
        } finally {
            // Delete the import if it was successful
            resourceStore.deleteTempFile(action.getKey());
        }

        return action.getKey();
    }
}
