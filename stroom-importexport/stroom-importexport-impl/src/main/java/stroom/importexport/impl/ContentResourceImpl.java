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
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.BadRequestException;

@AutoLogged
class ContentResourceImpl implements ContentResource {

    private final Provider<ImportExportService> importExportServiceProvider;
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;
    private final Provider<ResourceStore> resourceStoreProvider;
    private final Provider<DependencyService> dependencyServiceProvider;
    private final Provider<SecurityContext> securityContextProvider;

    @Inject
    ContentResourceImpl(final Provider<ImportExportService> importExportServiceProvider,
                        final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider,
                        final Provider<ResourceStore> resourceStoreProvider,
                        final Provider<DependencyService> dependencyServiceProvider,
                        final Provider<SecurityContext> securityContextProvider) {
        this.importExportServiceProvider = importExportServiceProvider;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
        this.resourceStoreProvider = resourceStoreProvider;
        this.dependencyServiceProvider = dependencyServiceProvider;
        this.securityContextProvider = securityContextProvider;
    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public ResourceKey importContent(final ImportConfigRequest request) {
        if (request.getConfirmList() == null || request.getConfirmList().isEmpty()) {
            throw new BadRequestException("Missing confirm list");
        }

        return stroomEventLoggingServiceProvider.get().loggedResult(
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
        return securityContextProvider.get().secureResult(PermissionNames.IMPORT_CONFIGURATION, () -> {
            ResourceStore resourceStore = resourceStoreProvider.get();
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

            importExportServiceProvider.get().performImportWithConfirmation(file, request.getConfirmList());

            // Delete the import if it was successful
            resourceStore.deleteTempFile(request.getResourceKey());

            return request.getResourceKey();
        });
    }

    @Override
    @AutoLogged(value = OperationType.UNKNOWN, verb = "Confirming what to import")
    public List<ImportState> confirmImport(final ResourceKey resourceKey) {
        return securityContextProvider.get().secureResult(PermissionNames.IMPORT_CONFIGURATION, () -> {
            try {
                final Path tempPath = resourceStoreProvider.get().getTempFile(resourceKey);
                return importExportServiceProvider.get().createImportConfirmationList(tempPath);
            } catch (final RuntimeException rex) {
                // In case of error delete the temp file
                resourceStoreProvider.get().deleteTempFile(resourceKey);
                throw rex;
            }
        });
    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public ResourceGeneration exportContent(final DocRefs docRefs) {
        Objects.requireNonNull(docRefs);

        return stroomEventLoggingServiceProvider.get().loggedResult(
                "ExportConfig",
                "Exporting Configuration",
                ExportEventAction.builder()
                        .withSource(MultiObject.builder()
                                .addCriteria(buildCriteria(docRefs))
                                .build())
                        .build(),
                () ->
                        securityContextProvider.get().secureResult(PermissionNames.EXPORT_CONFIGURATION, () -> {
                            final List<Message> messageList = new ArrayList<>();
                            ResourceStore resourceStore = resourceStoreProvider.get();
                            final ResourceKey guiKey = resourceStore.createTempFile("StroomConfig.zip");
                            final Path file = resourceStore.getTempFile(guiKey);
                            importExportServiceProvider.get().exportConfig(docRefs.getDocRefs(), file, messageList);

                            return new ResourceGeneration(guiKey, messageList);
                        }));
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public ResultPage<Dependency> fetchDependencies(final DependencyCriteria criteria) {
        return securityContextProvider.get().secureResult(() ->
                dependencyServiceProvider.get().getDependencies(criteria));
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
