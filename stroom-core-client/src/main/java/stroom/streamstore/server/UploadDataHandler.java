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

package stroom.streamstore.server;

import stroom.entity.server.util.EntityServiceExceptionUtil;
import stroom.security.Secured;
import stroom.servlet.SessionResourceStore;
import stroom.streamstore.server.udload.StreamUploadTask;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.UploadDataAction;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.task.server.TaskManager;
import stroom.util.shared.ResourceKey;

import javax.annotation.Resource;
import java.nio.file.Path;

@TaskHandlerBean(task = UploadDataAction.class)
@Secured(Stream.IMPORT_DATA_PERMISSION)
public class UploadDataHandler extends AbstractTaskHandler<UploadDataAction, ResourceKey> {
    @Resource
    private SessionResourceStore sessionResourceStore;
    @Resource
    private TaskManager taskManager;

    @Override
    public ResourceKey exec(final UploadDataAction action) {
        try {
            // Import file.
            final Path file = sessionResourceStore.getTempFile(action.getKey());

            taskManager.exec(new StreamUploadTask(action.getSessionId(), action.getUserId(), action.getFileName(), file,
                    action.getFeed(), action.getStreamType(), action.getEffectiveMs(), action.getMetaData()));

        } catch (final Exception ex) {
            throw EntityServiceExceptionUtil.create(ex);
        } finally {
            // Delete the import if it was successful
            sessionResourceStore.deleteTempFile(action.getKey());
        }

        return action.getKey();
    }
}
