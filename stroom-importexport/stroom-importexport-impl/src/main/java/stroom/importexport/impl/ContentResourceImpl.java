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

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.importexport.shared.ContentResource;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.importexport.shared.ImportConfigRequest;
import stroom.importexport.shared.ImportState;
import stroom.resource.api.ResourceStore;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.shared.DocRefs;
import stroom.util.shared.Message;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.ResultPage;

import event.logging.AdvancedQuery;
import event.logging.Criteria;
import event.logging.ExportEventAction;
import event.logging.ImportEventAction;
import event.logging.MultiObject;
import event.logging.Or;
import event.logging.OtherObject;
import event.logging.Query;
import event.logging.Term;
import event.logging.TermCondition;
import event.logging.util.EventLoggingUtil;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

class ContentResourceImpl implements ContentResource {
    private final ImportExportService importExportService;
    private final StroomEventLoggingService stroomEventLoggingService;
    private final ResourceStore resourceStore;
    private final DependencyService dependencyService;
    private final SecurityContext securityContext;

    @Inject
    ContentResourceImpl(final ImportExportService importExportService,
                        final StroomEventLoggingService stroomEventLoggingService,
                        final ResourceStore resourceStore,
                        final DependencyService dependencyService,
                        final SecurityContext securityContext) {
        this.importExportService = importExportService;
        this.stroomEventLoggingService = stroomEventLoggingService;
        this.resourceStore = resourceStore;
        this.dependencyService = dependencyService;
        this.securityContext = securityContext;
    }

    @Override
    public ResourceKey importContent(final ImportConfigRequest request) {
        if (request.getConfirmList() == null || request.getConfirmList().isEmpty()) {
            throw new BadRequestException("Missing confirm list");
        }

        return stroomEventLoggingService.loggedResult(
                "ImportConfig",
                "Importing Configuration",
                buildImportEventAction(request),
                () ->
                        performImport(request)
        );
    }

    private ImportEventAction buildImportEventAction(final ImportConfigRequest importConfigRequest) {
        final List<ImportState> confirmList = importConfigRequest.getConfirmList();

        return ImportEventAction.builder()
                .withSource(MultiObject.builder()
                        .addObject(confirmList.stream()
                                .map(importState -> OtherObject.builder()
                                        .withId(importState.getDocRef().getUuid())
                                        .withType(importState.getDocRef().getType())
                                        .withName(importState.getDocRef().getName())
                                        .addData(EventLoggingUtil.createData(
                                                "ImportAction",
                                                importState.getState() != null
                                                        ? importState.getState().getDisplayValue()
                                                        : "Error"))
                                        .build())
                                .collect(Collectors.toList()))
                        .build())
                .build();
    }

    private ResourceKey performImport(final ImportConfigRequest request) {
        return securityContext.secureResult(PermissionNames.IMPORT_CONFIGURATION, () -> {
            // Import file.
            final Path file = resourceStore.getTempFile(request.getResourceKey());

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
                final Path tempPath = resourceStore.getTempFile(resourceKey);
                return importExportService.createImportConfirmationList(tempPath);
            } catch (final RuntimeException rex) {
                // In case of error delete the temp file
                resourceStore.deleteTempFile(resourceKey);
                throw rex;
            }
        });
    }

    @Override
    public ResourceGeneration exportContent(final DocRefs docRefs) {
        Objects.requireNonNull(docRefs);

        return stroomEventLoggingService.loggedResult(
                "ExportConfig",
                "Exporting Configuration",
                ExportEventAction.builder()
                        .withSource(MultiObject.builder()
                                .addCriteria(buildCriteria(docRefs))
                                .build())
                        .build(),
                () ->
                        securityContext.secureResult(PermissionNames.EXPORT_CONFIGURATION, () -> {
                            final List<Message> messageList = new ArrayList<>();

                            final ResourceKey guiKey = resourceStore.createTempFile("StroomConfig.zip");
                            final Path file = resourceStore.getTempFile(guiKey);
                            importExportService.exportConfig(docRefs.getDocRefs(), file, messageList);

                            return new ResourceGeneration(guiKey, messageList);
                        }));
    }

    @Override
    public ResultPage<Dependency> fetchDependencies(final DependencyCriteria criteria) {
        return securityContext.secureResult(() ->
                dependencyService.getDependencies(criteria));
    }

    private Criteria buildCriteria(final DocRefs docRefs) {
        return Criteria.builder()
                .withQuery(Query.builder()
                        .withAdvanced(AdvancedQuery.builder()
                                .addOr(Or.builder()
                                        .addTerm(docRefs.getDocRefs()
                                                .stream()
                                                .map(docRef -> Term.builder()
                                                        .withName(docRef.getName())
                                                        .withCondition(TermCondition.EQUALS)
                                                        .withValue(docRef.getUuid())
                                                        .build())
                                                .collect(Collectors.toList()))
                                        .build())
                                .build())
                        .build())
                .build();
    }
}
