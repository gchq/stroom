package stroom.importexport.impl;

import stroom.explorer.shared.ExplorerConstants;
import stroom.resource.api.ResourceStore;
import stroom.security.api.SecurityContext;
import stroom.util.io.StreamUtil;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Set;

@Api(tags = "Export")
@Path("/export" + ResourcePaths.V1)
public class ExportConfigResource implements RestResource {
    private final transient Provider<ImportExportService> importExportServiceProvider;
    private final transient Provider<ResourceStore> resourceStoreProvider;
    private final transient Provider<ExportConfig> exportConfigProvider;
    private final transient Provider<SecurityContext> securityContextProvider;

    @Inject
    public ExportConfigResource(final Provider<ImportExportService> importExportServiceProvider,
                                final Provider<ResourceStore> resourceStoreProvider,
                                final Provider<ExportConfig> exportConfigProvider,
                                final Provider<SecurityContext> securityContextProvider) {
        this.importExportServiceProvider = importExportServiceProvider;
        this.resourceStoreProvider = resourceStoreProvider;
        this.exportConfigProvider = exportConfigProvider;
        this.securityContextProvider = securityContextProvider;
    }

    @GET
    @ApiOperation(
            value = "Exports all configuration to a file.",
            response = Void.class)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response export() {
        final ResourceStore resourceStore = resourceStoreProvider.get();
        if (!securityContextProvider.get().hasAppPermission("Export Configuration")) {
            throw new ClientErrorException("You do not have permission", Status.FORBIDDEN);
        }

        final boolean enabled = exportConfigProvider.get().isEnabled();
        if (enabled) {
            final ResourceKey tempResourceKey = resourceStore.createTempFile("StroomConfig.zip");

            try {
                final java.nio.file.Path tempFile = resourceStore.getTempFile(tempResourceKey);
                importExportServiceProvider.get()
                        .exportConfig(Set.of(ExplorerConstants.ROOT_DOC_REF), tempFile, new ArrayList<>());

                final StreamingOutput streamingOutput = output -> {
                    try (final InputStream is = Files.newInputStream(tempFile)) {
                        StreamUtil.streamToStream(is, output);
                    } finally {
                        resourceStore.deleteTempFile(tempResourceKey);
                    }
                };

                return Response
                        .ok(streamingOutput, MediaType.APPLICATION_OCTET_STREAM)
                        .header("Content-Disposition", "attachment; filename=\"" +
                                tempFile.getFileName().toString() + "\"")
                        .build();

            } catch (final EntityServiceException e) {
                return Response
                        .status(Status.NO_CONTENT.getStatusCode(), "Export is not enabled")
                        .build();
            }
        } else {
            throw new ClientErrorException("Export is not enabled", Status.FORBIDDEN);
        }
    }
}
