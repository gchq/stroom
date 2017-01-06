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

package stroom.importexport.server;

import stroom.entity.shared.EntityActionConfirmation;
import stroom.importexport.shared.ImportConfigAction;
import stroom.logging.ImportExportEventLog;
import stroom.security.Secured;
import stroom.servlet.SessionResourceStore;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.ResourceKey;

import javax.annotation.Resource;
import java.io.File;

@TaskHandlerBean(task = ImportConfigAction.class)
public class ImportConfigHandler extends AbstractTaskHandler<ImportConfigAction, ResourceKey> {
    @Resource
    private ImportExportService importExportService;
    @Resource
    private ImportExportEventLog eventLog;
    @Resource
    private SessionResourceStore sessionResourceStore;

    @Override
    @Secured("Import Configuration")
    public ResourceKey exec(final ImportConfigAction action) {
        // Import file.
        final File file = sessionResourceStore.getTempFile(action.getKey());

        // Log the import.
        eventLog._import(action);

        boolean foundOneAction = false;
        for (final EntityActionConfirmation entityActionConfirmation : action.getConfirmList()) {
            if (entityActionConfirmation.isAction()) {
                foundOneAction = true;
                break;
            }
        }
        if (!foundOneAction) {
            return action.getKey();
        }

        importExportService.performImportWithConfirmation(file, action.getConfirmList());

        // Delete the import if it was successful
        sessionResourceStore.deleteTempFile(action.getKey());

        return action.getKey();
    }
}
