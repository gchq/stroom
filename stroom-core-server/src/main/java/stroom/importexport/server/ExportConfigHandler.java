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

import stroom.entity.server.GenericEntityService;
import stroom.importexport.shared.ExportConfigAction;
import stroom.logging.ImportExportEventLog;
import stroom.security.Secured;
import stroom.servlet.SessionResourceStore;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@TaskHandlerBean(task = ExportConfigAction.class)
public class ExportConfigHandler extends AbstractTaskHandler<ExportConfigAction, ResourceGeneration> {
    @Resource
    private ImportExportService importExportService;
    @Resource
    private ImportExportEventLog eventLog;
    @Resource
    private SessionResourceStore sessionResourceStore;
    @Resource
    private GenericEntityService genericEntityService;

    @Override
    @Secured("Export Configuration")
    public ResourceGeneration exec(final ExportConfigAction action) {
        // Log the export.
        eventLog.export(action);
        final List<String> messageList = new ArrayList<String>();

        final ResourceKey guiKey = sessionResourceStore.createTempFile("StroomConfig.zip");
        final File file = sessionResourceStore.getTempFile(guiKey);
        importExportService.exportConfig(action.getCriteria(), file, action.isIgnoreErrors(), messageList);

        return new ResourceGeneration(guiKey, messageList);
    }
}
