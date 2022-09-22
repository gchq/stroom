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

package stroom.data.store.impl;

import stroom.data.shared.DataInfoSection;
import stroom.data.shared.DataResource;
import stroom.data.shared.StreamTypeNames;
import stroom.data.shared.UploadDataRequest;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.meta.shared.FindMetaCriteria;
import stroom.pipeline.shared.AbstractFetchDataResult;
import stroom.pipeline.shared.FetchDataRequest;
import stroom.pipeline.shared.FetchDataRequest.DisplayMode;
import stroom.pipeline.shared.FetchDataResult;
import stroom.resource.api.ResourceStore;
import stroom.util.EntityServiceExceptionUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Count;
import stroom.util.shared.FetchWithLongId;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;

import event.logging.ComplexLoggedOutcome;
import event.logging.ExportEventAction;
import event.logging.File;
import event.logging.ImportEventAction;
import event.logging.MultiObject;
import event.logging.ViewEventAction;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

@AutoLogged
class DataResourceImpl implements DataResource, FetchWithLongId<List<DataInfoSection>> {

    private final Provider<DataService> dataServiceProvider;
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;
    private final Provider<ResourceStore> resourceStoreProvider;

    @Inject
    DataResourceImpl(final Provider<DataService> dataServiceProvider,
                     final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider,
                     final Provider<ResourceStore> resourceStoreProvider) {
        this.dataServiceProvider = dataServiceProvider;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
        this.resourceStoreProvider = resourceStoreProvider;
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public ResourceGeneration download(final FindMetaCriteria criteria) {

        final StroomEventLoggingService stroomEventLoggingService = stroomEventLoggingServiceProvider.get();

        final ExportEventAction exportEventAction = ExportEventAction.builder()
                .withSource(MultiObject.builder()
                        .addCriteria(stroomEventLoggingService.convertExpressionCriteria(
                                "Meta",
                                criteria))
                        .build())
                .build();

        return stroomEventLoggingServiceProvider.get()
                .loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "download"))
                .withDescription("Downloading stream data")
                .withDefaultEventAction(exportEventAction)
                .withSimpleLoggedResult(() -> {
                    try {
                        return dataServiceProvider.get().download(criteria);
                    } catch (final RuntimeException e) {
                        throw EntityServiceExceptionUtil.create(e);
                    }
                })
                .getResultAndLog();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public Response downloadZip(final FindMetaCriteria criteria) {

        final ResourceGeneration resourceGeneration = dataServiceProvider.get().download(criteria);
        final ResourceStore resourceStore = resourceStoreProvider.get();
        final ResourceKey resourceKey = resourceGeneration.getResourceKey();
        final Path tempFile = resourceStore.getTempFile(resourceKey);

        try {
            final ExportEventAction exportEventAction = ExportEventAction.builder()
                    .withSource(MultiObject.builder()
                            .addCriteria(stroomEventLoggingServiceProvider.get().convertExpressionCriteria(
                                    "Meta",
                                    criteria))
                            .addFile(File.builder()
                                    .withName(tempFile.getFileName().toString())
                                    .withSize(BigInteger.valueOf(Files.size(tempFile)))
                                    .build())
                            .build())
                    .build();

            return stroomEventLoggingServiceProvider.get()
                    .loggedWorkBuilder()
                    .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "downloadZip"))
                    .withDescription("Downloading stream data as zip")
                    .withDefaultEventAction(exportEventAction)
                    .withSimpleLoggedResult(() -> {
                        // Stream the downloaded content to the client as ZIP data
                        final StreamingOutput streamingOutput = output -> {
                            try (final InputStream is = Files.newInputStream(tempFile)) {
                                StreamUtil.streamToStream(is, output);
                            } finally {
                                resourceStore.deleteTempFile(resourceKey);
                            }
                        };

                        return Response
                                .ok(streamingOutput, MediaType.APPLICATION_OCTET_STREAM)
                                .header("Content-Disposition", "attachment; filename=\"" +
                                        tempFile.getFileName().toString() + "\"")
                                .build();
                    })
                    .getResultAndLog();
        } catch (IOException e) {
            throw EntityServiceExceptionUtil.create(e);
        }
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public ResourceKey upload(final UploadDataRequest request) {

        final StroomEventLoggingService stroomEventLoggingService = stroomEventLoggingServiceProvider.get();

        final ResourceKey resourceKey = stroomEventLoggingService.loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "upload"))
                .withDescription("Uploading stream data")
                .withDefaultEventAction(ImportEventAction.builder()
                        .withSource(stroomEventLoggingService.convertToMulti(request))
                        .build())
                .withSimpleLoggedResult(() -> {
                    try {
                        return dataServiceProvider.get().upload(request);
                    } catch (final RuntimeException e) {
                        throw EntityServiceExceptionUtil.create(e);
                    }
                })
                .getResultAndLog();

        return resourceKey;
    }

    @Override
    public List<DataInfoSection> viewInfo(final long id) {
        final List<DataInfoSection> result;
        try {
            result = dataServiceProvider.get().info(id);
        } catch (final RuntimeException e) {
            throw EntityServiceExceptionUtil.create(e);
        }

        return result;
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public AbstractFetchDataResult fetch(final FetchDataRequest request) {
        final String idStr = request.getSourceLocation() != null
                ? request.getSourceLocation().getIdentifierString()
                : "?";
        final StroomEventLoggingService stroomEventLoggingService = stroomEventLoggingServiceProvider.get();

        return stroomEventLoggingService.loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "fetch"))
                .withDescription("Viewing stream " + idStr)
                .withDefaultEventAction(ViewEventAction.builder()
                        .build())
                .withComplexLoggedResult(eventAction -> {
                    ComplexLoggedOutcome<AbstractFetchDataResult, ViewEventAction> outcome;
                    try {
                        // Do the fetch
                        final AbstractFetchDataResult fetchDataResult = dataServiceProvider.get()
                                .fetch(request);

                        outcome = ComplexLoggedOutcome.success(
                                fetchDataResult,
                                ViewEventAction.builder()
                                        .withObjects(stroomEventLoggingService.convert(fetchDataResult))
                                        .build());

                    } catch (ViewDataException vde) {
                        // Convert an ex into a fetch result
                        final AbstractFetchDataResult fetchDataResult = createErrorResult(vde);
                        outcome = ComplexLoggedOutcome.failure(
                                fetchDataResult,
                                ViewEventAction.builder()
                                        .withObjects(stroomEventLoggingService.convert(fetchDataResult))
                                        .build(),
                                vde.getMessage());
                    }
                    return outcome;
                })
                .getResultAndLog();
    }

    @AutoLogged(OperationType.UNLOGGED) // Not an explicit user action
    @Override
    public Set<String> getChildStreamTypes(final long id, final long partNo) {

        final Set<String> childStreamTypes = dataServiceProvider.get()
                .getChildStreamTypes(id, partNo);

        return childStreamTypes;
    }

    private FetchDataResult createErrorResult(final ViewDataException viewDataException) {
        return new FetchDataResult(
                null,
                StreamTypeNames.RAW_EVENTS,
                null,
                viewDataException.getSourceLocation(),
                OffsetRange.zero(),
                Count.of(0L, true),
                Count.of(0L, true),
                0L,
                null,
                null,
                false,
                null,
                DisplayMode.TEXT,
                Collections.singletonList(viewDataException.getMessage()));
    }

    @Override
    public List<DataInfoSection> fetch(final Long id) {
        // Provide the info when failing to read the info
        return viewInfo(id);
    }
}
