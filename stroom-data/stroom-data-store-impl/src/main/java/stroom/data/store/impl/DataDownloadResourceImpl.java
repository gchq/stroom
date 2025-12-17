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

package stroom.data.store.impl;

import stroom.data.store.api.DataDownloadResource;
import stroom.data.store.api.DataService;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.meta.shared.FindMetaCriteria;
import stroom.resource.api.ResourceStore;
import stroom.util.EntityServiceExceptionUtil;
import stroom.util.io.StreamUtil;
import stroom.util.logging.NoResponseBodyLogging;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;

import event.logging.ComplexLoggedOutcome;
import event.logging.ExportEventAction;
import event.logging.File;
import event.logging.MultiObject;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;

public class DataDownloadResourceImpl implements DataDownloadResource {

    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;
    private final Provider<ResourceStore> resourceStoreProvider;
    private final Provider<DataService> dataServiceProvider;

    @Inject
    DataDownloadResourceImpl(final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider,
                             final Provider<ResourceStore> resourceStoreProvider,
                             final Provider<DataService> dataServiceProvider) {
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
        this.resourceStoreProvider = resourceStoreProvider;
        this.dataServiceProvider = dataServiceProvider;
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @NoResponseBodyLogging
    @Override
    public Response downloadZip(final FindMetaCriteria criteria) {

        return stroomEventLoggingServiceProvider.get()
                .loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "downloadZip"))
                .withDescription("Downloading stream data as zip")
                .withDefaultEventAction(ExportEventAction.builder().build())
                .withComplexLoggedResult(eventAction -> {
                    try {
                        final ResourceGeneration resourceGeneration = dataServiceProvider.get().download(criteria);
                        final ResourceStore resourceStore = resourceStoreProvider.get();
                        final ResourceKey resourceKey = resourceGeneration.getResourceKey();
                        final Path tempFile = resourceStore.getTempFile(resourceKey);

                        final ExportEventAction exportEventAction = eventAction.newCopyBuilder()
                                .withSource(MultiObject.builder()
                                        .addCriteria(stroomEventLoggingServiceProvider.get()
                                                .convertExpressionCriteria("Meta", criteria))
                                        .addFile(File.builder()
                                                .withName(resourceKey.getName())
                                                .withSize(BigInteger.valueOf(Files.size(tempFile)))
                                                .build())
                                        .build())
                                .build();

                        // Stream the downloaded content to the client as ZIP data
                        final StreamingOutput streamingOutput = output -> {
                            try (final InputStream is = Files.newInputStream(tempFile)) {
                                StreamUtil.streamToStream(is, output);
                            } finally {
                                resourceStore.deleteTempFile(resourceKey);
                            }
                        };

                        final Response response = Response
                                .ok(streamingOutput, MediaType.APPLICATION_OCTET_STREAM)
                                .header("Content-Disposition", "attachment; filename=\"" + resourceKey.getName() + "\"")
                                .build();

                        return ComplexLoggedOutcome.success(response, exportEventAction);
                    } catch (final IOException e) {
                        throw EntityServiceExceptionUtil.create(e);
                    }
                })
                .getResultAndLog();
    }
}
