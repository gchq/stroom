/*
 * Copyright 2017 Crown Copyright
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

package stroom.importexport.impl;

import com.codahale.metrics.health.HealthCheck.Result;
import stroom.importexport.shared.ContentResource;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.importexport.shared.ImportConfigRequest;
import stroom.importexport.shared.ImportState;
import stroom.resource.api.ResourceStore;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.HasHealthCheck;
import stroom.util.shared.DocRefs;
import stroom.util.shared.Message;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class ContentResourceImpl implements ContentResource, HasHealthCheck {
    private final ImportExportService importExportService;
    private final ImportExportEventLog eventLog;
    private final ResourceStore resourceStore;
    private final DependencyService dependencyService;
    private final SecurityContext securityContext;

    @Inject
    ContentResourceImpl(final ImportExportService importExportService,
                        final ImportExportEventLog eventLog,
                        final ResourceStore resourceStore,
                        final DependencyService dependencyService,
                        final SecurityContext securityContext) {
        this.importExportService = importExportService;
        this.eventLog = eventLog;
        this.resourceStore = resourceStore;
        this.dependencyService = dependencyService;
        this.securityContext = securityContext;
    }

    @Override
    public ResourceKey importContent(final ImportConfigRequest request) {
        return securityContext.secureResult(PermissionNames.IMPORT_CONFIGURATION, () -> {
            // Import file.
            final Path file = resourceStore.getTempFile(request.getResourceKey());

            // Log the import.
            eventLog._import(request);

            boolean foundOneAction = false;
            for (final ImportState importState : request.getConfirmList()) {
                if (importState.isAction()) {
                    foundOneAction = true;
                    break;
                }
            }
            if (!foundOneAction) {
                return request.getResourceKey();
            }

            importExportService.performImportWithConfirmation(file, request.getConfirmList());

            // Delete the import if it was successful
            resourceStore.deleteTempFile(request.getResourceKey());

            return request.getResourceKey();
        });
    }

    @Override
    public List<ImportState> confirmImport(final ResourceKey resourceKey) {
        return securityContext.secureResult(PermissionNames.IMPORT_CONFIGURATION, () -> {
            try {
                return importExportService.createImportConfirmationList(resourceStore.getTempFile(resourceKey));
            } catch (final RuntimeException rex) {
                // In case of error delete the temp file
                resourceStore.deleteTempFile(resourceKey);
                throw rex;
            }
        });
    }

    @Override
    public ResourceGeneration exportContent(final DocRefs docRefs) {
        return securityContext.secureResult(PermissionNames.EXPORT_CONFIGURATION, () -> {
            // Log the export.
            eventLog.export(docRefs);
            final List<Message> messageList = new ArrayList<>();

            final ResourceKey guiKey = resourceStore.createTempFile("StroomConfig.zip");
            final Path file = resourceStore.getTempFile(guiKey);
            importExportService.exportConfig(docRefs.getDocRefs(), file, messageList);

            return new ResourceGeneration(guiKey, messageList);
        });
    }

    @Override
    public ResultPage<Dependency> fetchDependencies(final DependencyCriteria criteria) {
        return securityContext.secureResult(() -> dependencyService.getDependencies(criteria));
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}