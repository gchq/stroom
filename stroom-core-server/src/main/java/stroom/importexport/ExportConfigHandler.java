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

import stroom.importexport.shared.ExportConfigAction;
import stroom.logging.ImportExportEventLog;
import stroom.resource.ResourceStore;
import stroom.security.Secured;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.util.shared.Message;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@TaskHandlerBean(task = ExportConfigAction.class)
class ExportConfigHandler extends AbstractTaskHandler<ExportConfigAction, ResourceGeneration> {
    private final ImportExportService importExportService;
    private final ImportExportEventLog eventLog;
    private final ResourceStore resourceStore;

    @Inject
    ExportConfigHandler(final ImportExportService importExportService,
                        final ImportExportEventLog eventLog,
                        final ResourceStore resourceStore) {
        this.importExportService = importExportService;
        this.eventLog = eventLog;
        this.resourceStore = resourceStore;
    }

    @Override
    @Secured("Export Configuration")
    public ResourceGeneration exec(final ExportConfigAction action) {
        // Log the export.
        eventLog.export(action);
        final List<Message> messageList = new ArrayList<>();

        final ResourceKey guiKey = resourceStore.createTempFile("StroomConfig.zip");
        final Path file = resourceStore.getTempFile(guiKey);
        importExportService.exportConfig(action.getDocRefs(), file, messageList);

        return new ResourceGeneration(guiKey, messageList);
    }
}
