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

import stroom.importexport.shared.ImportConfigConfirmationAction;
import stroom.importexport.shared.ImportState;
import stroom.resource.ResourceStore;
import stroom.security.Secured;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.util.shared.SharedList;

import javax.inject.Inject;

@TaskHandlerBean(task = ImportConfigConfirmationAction.class)
class ImportConfigConfirmationHandler
        extends AbstractTaskHandler<ImportConfigConfirmationAction, SharedList<ImportState>> {
    private final ImportExportService importExportService;
    private final ResourceStore resourceStore;

    @Inject
    ImportConfigConfirmationHandler(final ImportExportService importExportService,
                                    final ResourceStore resourceStore) {
        this.importExportService = importExportService;
        this.resourceStore = resourceStore;
    }

    @Override
    @Secured("Import Configuration")
    public SharedList<ImportState> exec(final ImportConfigConfirmationAction task) {
        try {
            return importExportService.createImportConfirmationList(resourceStore.getTempFile(task.getKey()));
        } catch (final RuntimeException rex) {
            // In case of error delete the temp file
            resourceStore.deleteTempFile(task.getKey());
            throw rex;
        }
    }
}
