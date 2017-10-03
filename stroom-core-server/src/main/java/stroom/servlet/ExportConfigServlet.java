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

import org.springframework.stereotype.Component;
import stroom.entity.shared.DocRefs;
import stroom.entity.shared.Folder;
import stroom.importexport.server.ImportExportService;
import stroom.node.server.StroomPropertyService;
import stroom.query.api.v2.DocRef;
import stroom.resource.server.ResourceStore;
import stroom.util.io.StreamUtil;
import stroom.util.shared.ResourceKey;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/**
 * B2B Export of the config
 */
@Component
public class ExportConfigServlet extends HttpServlet {
    private static final long serialVersionUID = -4533441835216235920L;
    private static final String PROPERTY = "stroom.export.enabled";

    private final transient ImportExportService importExportService;
    private final transient ResourceStore resourceStore;
    private final transient StroomPropertyService propertyService;

    @Inject
    public ExportConfigServlet(final ImportExportService importExportService,
                               @Named("resourceStore") final ResourceStore resourceStore,
                               final StroomPropertyService propertyService) {
        this.importExportService = importExportService;
        this.resourceStore = resourceStore;
        this.propertyService = propertyService;
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {
        final boolean enabled = propertyService.getBooleanProperty(PROPERTY, false);
        if (enabled) {
            final ResourceKey tempResourceKey = resourceStore.createTempFile("StroomConfig.zip");

            try {
                final Path tempFile = resourceStore.getTempFile(tempResourceKey);

                final DocRefs docRefs = new DocRefs();
                docRefs.add(new DocRef(Folder.ENTITY_TYPE, "0", "System"));

                importExportService.exportConfig(docRefs, tempFile, new ArrayList<>());

                StreamUtil.streamToStream(Files.newInputStream(tempFile), resp.getOutputStream(), true);

            } finally {
                resourceStore.deleteTempFile(tempResourceKey);
            }
        } else {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Export is not enabled");
        }
    }
}
