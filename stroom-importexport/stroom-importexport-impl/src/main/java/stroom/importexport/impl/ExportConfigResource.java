package stroom.importexport.impl;

import io.swagger.annotations.Api;
import stroom.explorer.shared.ExplorerConstants;
import stroom.resource.api.ResourceStore;
import stroom.security.api.SecurityContext;
import stroom.util.io.StreamUtil;
import stroom.util.shared.DocRefs;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.RestResource;

import javax.inject.Inject;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;

@Api(value = "export - /v1")
@Path("/export/v1")
public class ExportConfigResource implements RestResource {
    private final transient ImportExportService importExportService;
    private final transient ResourceStore resourceStore;
    private final transient ExportConfig exportConfig;
    private final transient SecurityContext securityContext;

    @Inject
    public ExportConfigResource(final ImportExportService importExportService,
                                final ResourceStore resourceStore,
                                final ExportConfig exportConfig,
                                final SecurityContext securityContext) {
        this.importExportService = importExportService;
        this.resourceStore = resourceStore;
        this.exportConfig = exportConfig;
        this.securityContext = securityContext;
    }

    @GET
    public Response export() {
        if (!securityContext.hasAppPermission("Export Configuration")) {
            throw new ClientErrorException("You do not have permission", Status.FORBIDDEN);
        }

        final boolean enabled = exportConfig.isEnabled();
        if (enabled) {
            final ResourceKey tempResourceKey = resourceStore.createTempFile("StroomConfig.zip");

            try {
                final java.nio.file.Path tempFile = resourceStore.getTempFile(tempResourceKey);

                final DocRefs docRefs = new DocRefs();
                docRefs.add(ExplorerConstants.ROOT_DOC_REF);

                importExportService.exportConfig(docRefs, tempFile, new ArrayList<>());

                final StreamingOutput streamingOutput = output -> {
                    try (final InputStream is = Files.newInputStream(tempFile)) {
                        StreamUtil.streamToStream(is, output);
                    }
                };

                return Response
                        .ok(streamingOutput, MediaType.APPLICATION_OCTET_STREAM)
                        .header("Content-Disposition", "attachment; filename=\"" + tempFile.getFileName().toString() + "\"")
                        .build();

            } catch (final EntityServiceException e) {
                return Response
                        .status(Status.NO_CONTENT.getStatusCode(), "Export is not enabled")
                        .build();
            } finally {
                resourceStore.deleteTempFile(tempResourceKey);
            }
        } else {
            throw new ClientErrorException("Export is not enabled", Status.FORBIDDEN);
        }
    }
}
