package stroom.servlet;

import io.swagger.annotations.Api;
import stroom.entity.shared.DocRefs;
import stroom.entity.shared.EntityServiceException;
import stroom.explorer.shared.ExplorerConstants;
import stroom.importexport.ImportExportService;
import stroom.resource.ResourceStore;
import stroom.security.SecurityContext;
import stroom.util.io.StreamUtil;
import stroom.util.shared.ResourceKey;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;

@Api(
        value = "export - /v1",
        description = "Export API")
@Path("/export/v1")
public class ExportConfigResource {
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
    public void export(@Context HttpServletRequest request,
                       @Context HttpServletResponse response) throws IOException {
        if (!securityContext.hasAppPermission("Export Configuration")) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "You do not have permission");
            return;
        }

        final boolean enabled = exportConfig.isEnabled();
        if (enabled) {
            final ResourceKey tempResourceKey = resourceStore.createTempFile("StroomConfig.zip");

            try {
                final java.nio.file.Path tempFile = resourceStore.getTempFile(tempResourceKey);

                final DocRefs docRefs = new DocRefs();
                docRefs.add(ExplorerConstants.ROOT_DOC_REF);

                importExportService.exportConfig(docRefs, tempFile, new ArrayList<>());

                try (final InputStream is = Files.newInputStream(tempFile); final OutputStream os = response.getOutputStream()) {
                    StreamUtil.streamToStream(is, os);
                }
            } catch (final EntityServiceException e) {
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            } finally {
                resourceStore.deleteTempFile(tempResourceKey);
            }
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Export is not enabled");
        }
    }
}
