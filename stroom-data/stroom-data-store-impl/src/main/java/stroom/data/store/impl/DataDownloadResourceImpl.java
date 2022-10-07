package stroom.data.store.impl;

import stroom.data.store.api.DataDownloadResource;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.meta.shared.FindMetaCriteria;
import stroom.resource.api.ResourceStore;
import stroom.util.EntityServiceExceptionUtil;
import stroom.util.io.StreamUtil;
import stroom.util.logging.NoResponseLogging;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;

import event.logging.ExportEventAction;
import event.logging.File;
import event.logging.MultiObject;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

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
    @NoResponseLogging
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
}
