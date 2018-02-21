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

package stroom.importexport;

import stroom.importexport.shared.ImportConfigAction;
import stroom.importexport.shared.ImportState;
import stroom.logging.ImportExportEventLog;
import stroom.security.Secured;
import stroom.servlet.SessionResourceStore;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.util.shared.ResourceKey;

import javax.inject.Inject;
import java.nio.file.Path;

@TaskHandlerBean(task = ImportConfigAction.class)
class ImportConfigHandler extends AbstractTaskHandler<ImportConfigAction, ResourceKey> {
    private final ImportExportService importExportService;
    private final ImportExportEventLog eventLog;
    private final SessionResourceStore sessionResourceStore;

    @Inject
    ImportConfigHandler(final ImportExportService importExportService,
                        final ImportExportEventLog eventLog,
                        final SessionResourceStore sessionResourceStore) {
        this.importExportService = importExportService;
        this.eventLog = eventLog;
        this.sessionResourceStore = sessionResourceStore;
    }

    @Override
    @Secured("Import Configuration")
    public ResourceKey exec(final ImportConfigAction action) {
        // Import file.
        final Path file = sessionResourceStore.getTempFile(action.getKey());

        // Log the import.
        eventLog._import(action);

        boolean foundOneAction = false;
        for (final ImportState importState : action.getConfirmList()) {
            if (importState.isAction()) {
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
