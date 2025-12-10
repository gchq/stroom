/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerConstants;
import stroom.importexport.api.ContentService;
import stroom.importexport.api.ExportSummary;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.importexport.shared.ImportConfigRequest;
import stroom.importexport.shared.ImportConfigResponse;
import stroom.importexport.shared.ImportSettings.ImportMode;
import stroom.importexport.shared.ImportState;
import stroom.resource.api.ResourceStore;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.logging.AsciiTable;
import stroom.util.logging.AsciiTable.Column;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.DocRefs;
import stroom.util.shared.Message;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.ResultPage;

import io.vavr.Tuple;
import io.vavr.Tuple3;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
class ContentServiceImpl implements ContentService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ContentServiceImpl.class);

    private final ImportExportService importExportService;
    private final ResourceStore resourceStore;
    private final DependencyService dependencyService;
    private final SecurityContext securityContext;
    private final Provider<ExportConfig> exportConfigProvider;

    @Inject
    ContentServiceImpl(final ImportExportService importExportService,
                       final Provider<ExportConfig> exportConfigProvider,
                       final ResourceStore resourceStore,
                       final DependencyService dependencyService,
                       final SecurityContext securityContext) {
        this.importExportService = importExportService;
        this.resourceStore = resourceStore;
        this.dependencyService = dependencyService;
        this.securityContext = securityContext;
        this.exportConfigProvider = exportConfigProvider;
    }

    @Override
    public ImportConfigResponse importContent(final ImportConfigRequest request) {
        return securityContext.secureResult(AppPermission.IMPORT_CONFIGURATION, () -> {
            try {
                // Import file.
                final Path tempFile = resourceStore.getTempFile(request.getResourceKey());

                //            boolean foundOneAction = false;
                //            for (final ImportState importState : confirmList) {
                //                if (importState.isAction()) {
                //                    foundOneAction = true;
                //                    break;
                //                }
                //            }
                //            if (!foundOneAction) {
                //                return resourceKey;
                //            }

                final List<ImportState> result = importExportService
                        .importConfig(tempFile, request.getImportSettings(), request.getConfirmList());

                if (!ImportMode.CREATE_CONFIRMATION.equals(request.getImportSettings().getImportMode())) {
                    // Delete the import if it was successful
                    resourceStore.deleteTempFile(request.getResourceKey());
                }

                return new ImportConfigResponse(request.getResourceKey(), result);
            } catch (final RuntimeException rex) {
                // In case of error delete the temp file
                resourceStore.deleteTempFile(request.getResourceKey());
                throw rex;
            }
        });
    }

    @Override
    public void abortImport(final ResourceKey resourceKey) {
        if (resourceKey != null) {
            try {
                resourceStore.deleteTempFile(resourceKey);
            } catch (final Exception e) {
                // Just log and swallow as it is only a temp file
                LOGGER.error("Unable to delete resourceKey {}: {}", resourceKey, LogUtil.exceptionMessage(e), e);
            }
        }
    }

//    @Override
//    public List<ImportState> confirmImport(final ResourceKey resourceKey,
//                                           final ImportSettings importSettings,
//                                           final List<ImportState> confirmList) {
//        return securityContext.secureResult(AppPermissionEnum.IMPORT_CONFIGURATION, () -> {
//            try {
//                final Path tempPath = resourceStore.getTempFile(resourceKey);
//                return importExportService.importConfig(tempPath, importSettings, confirmList);
//            } catch (final RuntimeException rex) {
//                // In case of error delete the temp file
//                resourceStore.deleteTempFile(resourceKey);
//                throw rex;
//            }
//        });
//    }

    @Override
    public ResourceGeneration exportContent(final DocRefs docRefs) {
        Objects.requireNonNull(docRefs);

        return securityContext.secureResult(AppPermission.EXPORT_CONFIGURATION, () -> {
            final ResourceStore resourceStore = this.resourceStore;
            final ResourceKey resourceKey = resourceStore.createTempFile("StroomConfig.zip");
            final Path tempFile = resourceStore.getTempFile(resourceKey);
            final ExportSummary exportSummary = importExportService.exportConfig(docRefs.getDocRefs(), tempFile);
            final List<Message> messageList = exportSummary.getMessages();

            return new ResourceGeneration(resourceKey, messageList);
        });
    }

    @Override
    public ResultPage<Dependency> fetchDependencies(final DependencyCriteria criteria) {
        return securityContext.secureResult(() -> dependencyService.getDependencies(criteria));
    }

    @Override
    public Map<DocRef, Set<DocRef>> fetchBrokenDependencies() {
        return dependencyService.getBrokenDependencies();
    }

    @Override
    public ResourceKey exportAll() {
        if (!securityContext.hasAppPermission(AppPermission.EXPORT_CONFIGURATION)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to export all config");
        }
        final ExportConfig exportConfig = exportConfigProvider.get();
        if (!exportConfig.isEnabled()) {
            LOGGER.warn("Attempt by user '{}' to export all data when {} is not enabled.",
                    securityContext.getUserRef(),
                    exportConfig.getFullPathStr(ExportConfig.ENABLED_PROP_NAME));
            throw new PermissionException(securityContext.getUserRef(), "Export is not enabled");
        }

        final ResourceKey tempResourceKey = resourceStore.createTempFile("StroomConfig.zip");
        final Path tempFile = resourceStore.getTempFile(tempResourceKey);

        LOGGER.info("Exporting all config to temp file {}", tempFile);
        final ExportSummary exportSummary = importExportService.exportConfig(
                Set.of(ExplorerConstants.SYSTEM_DOC_REF),
                tempFile);

        logSummary(tempFile, exportSummary);

        return tempResourceKey;
    }

    private void logSummary(final Path tempFile, final ExportSummary exportSummary) {
        final Set<String> allDocTypes = Stream.concat(
                        exportSummary.getSuccessCountsByType().keySet().stream(),
                        exportSummary.getFailedCountsByType().keySet().stream())
                .collect(Collectors.toSet());

//        final String typeCountsText = allDocTypes.stream()
//                .sorted()
//                .map(docType -> "  " + docType + ": "
//                        + Objects.requireNonNullElse(exportSummary.getSuccessCountsByType().get(docType), 0)
//                        + " (failed: "
//                        + Objects.requireNonNullElse(exportSummary.getFailedCountsByType().get(docType), 0)
//                        + ")")
//                .collect(Collectors.joining("\n"));

        final List<Tuple3<String, Integer, Integer>> tableData = allDocTypes.stream()
                .sorted()
                .map(docType -> Tuple.of(docType,
                        Objects.requireNonNullElse(exportSummary.getSuccessCountsByType().get(docType), 0),
                        Objects.requireNonNullElse(exportSummary.getFailedCountsByType().get(docType), 0)))
                .toList();

        final String typeCountsText = AsciiTable.builder(tableData)
                .withColumn(Column.of("Type", Tuple3::_1))
                .withColumn(Column.integer("Success", Tuple3::_2))
                .withColumn(Column.integer("Failed", Tuple3::_3))
                .build();

//        final String msgText = exportSummary.getMessages().isEmpty()
//                ? "no messages"
//                : "messages:\n" + exportSummary.getMessages().stream()
//                        .map(msg -> "  " + msg.toString())
//                        .collect(Collectors.joining("\n"));

        final String msgText = exportSummary.getMessages().isEmpty()
                ? "no messages"
                : "messages:\n" + AsciiTable.builder(exportSummary.getMessages())
                        .withColumn(Column.of("Severity", Message::getSeverity))
                        .withColumn(Column.of("Message", Message::getMessage))
                        .build();

        LOGGER.info("Exported {} documents ({} failures) using temp file {}, counts by type:\n{}\n{}",
                exportSummary.getSuccessTotal(),
                exportSummary.getFailedTotal(),
                tempFile,
                typeCountsText,
                msgText);
    }
}
