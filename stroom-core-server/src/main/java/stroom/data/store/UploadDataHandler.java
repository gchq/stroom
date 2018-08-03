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

package stroom.data.store;

import stroom.entity.util.EntityServiceExceptionUtil;
import stroom.resource.ResourceStore;
import stroom.security.shared.PermissionNames;
import stroom.security.Security;
import stroom.streamstore.shared.UploadDataAction;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskHandlerBean;
import stroom.task.TaskManager;
import stroom.util.shared.ResourceKey;

import javax.inject.Inject;
import java.nio.file.Path;

@TaskHandlerBean(task = UploadDataAction.class)
class UploadDataHandler extends AbstractTaskHandler<UploadDataAction, ResourceKey> {
    private final ResourceStore resourceStore;
    private final TaskManager taskManager;
    private final Security security;

    @Inject
    UploadDataHandler(final ResourceStore resourceStore,
                      final TaskManager taskManager,
                      final Security security) {
        this.resourceStore = resourceStore;
        this.taskManager = taskManager;
        this.security = security;
    }

    @Override
    public ResourceKey exec(final UploadDataAction action) {
        return security.secureResult(PermissionNames.IMPORT_DATA_PERMISSION, () -> {
            try {
                // Import file.
                final Path file = resourceStore.getTempFile(action.getKey());

                taskManager.exec(new StreamUploadTask(action.getUserToken(), action.getFileName(), file,
                        action.getFeedName(), action.getStreamTypeName(), action.getEffectiveMs(), action.getMetaData()));

            } catch (final RuntimeException e) {
                throw EntityServiceExceptionUtil.create(e);
            } finally {
                // Delete the import if it was successful
                resourceStore.deleteTempFile(action.getKey());
            }

            return action.getKey();
        });
    }
}
