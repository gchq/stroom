package stroom.importexport.impl;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.importexport.api.ContentService;
import stroom.resource.api.ResourceStore;
import stroom.util.io.StreamUtil;
import stroom.util.shared.ResourceKey;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

@AutoLogged
public class ExportContentResourceImpl implements ExportContentResource {

    private final Provider<ContentService> contentServiceProvider;
    private final Provider<ResourceStore> resourceStoreProvider;

    @Inject
    public ExportContentResourceImpl(Provider<ContentService> contentServiceProvider,
                                     Provider<ResourceStore> resourceStoreProvider) {
        this.contentServiceProvider = contentServiceProvider;
        this.resourceStoreProvider = resourceStoreProvider;
    }

    @Override
    @AutoLogged(value = OperationType.EXPORT, verb = "Exporting All Config")
    public Response export() {

        ResourceKey tempResourceKey = contentServiceProvider.get().exportAll();
        final Path tempFile = resourceStoreProvider.get().getTempFile(tempResourceKey);

        final StreamingOutput streamingOutput = output -> {
            try (final InputStream is = Files.newInputStream(tempFile)) {
                StreamUtil.streamToStream(is, output);
            } finally {
                resourceStoreProvider.get().deleteTempFile(tempResourceKey);
            }
        };

        return Response
                .ok(streamingOutput, MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment; filename=\"" +
                        tempFile.getFileName().toString() + "\"")
                .build();
    }
}
