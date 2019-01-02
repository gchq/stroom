/*
 * Copyright 2016 Crown Copyright
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

package stroom.servlet;

import io.swagger.annotations.Api;
import stroom.entity.shared.DocRefs;
import stroom.entity.shared.EntityServiceException;
import stroom.explorer.shared.ExplorerConstants;
import stroom.security.SecurityContext;
import stroom.importexport.ImportExportService;
import stroom.resource.ResourceStore;
import stroom.util.io.StreamUtil;
import stroom.util.shared.ResourceKey;

import javax.inject.Inject;
import javax.inject.Named;
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
    private static final String PROPERTY = "stroom.export.enabled";

    private final transient ImportExportService importExportService;
    private final transient ResourceStore resourceStore;
    private final transient ExportConfig exportConfig;
    private final transient SecurityContext securityContext;

    @Inject
    public ExportConfigResource(final ImportExportService importExportService,
                               @Named("resourceStore") final ResourceStore resourceStore,
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

                try (final InputStream inputStream = Files.newInputStream(tempFile);
                     final OutputStream outputStream = response.getOutputStream()) {
                    StreamUtil.streamToStream(inputStream, outputStream);
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
