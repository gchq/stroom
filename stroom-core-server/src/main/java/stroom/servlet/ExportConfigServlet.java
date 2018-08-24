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

import stroom.entity.shared.DocRefs;
import stroom.explorer.shared.ExplorerConstants;
import stroom.importexport.ImportExportService;
import stroom.resource.ResourceStore;
import stroom.util.io.StreamUtil;
import stroom.util.shared.ResourceKey;

import javax.inject.Inject;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/**
 * B2B Export of the config
 */
public class ExportConfigServlet extends HttpServlet {
    private static final long serialVersionUID = -4533441835216235920L;

    private final transient ImportExportService importExportService;
    private final transient ResourceStore resourceStore;
    private final transient ExportConfig exportConfig;

    @Inject
    ExportConfigServlet(final ImportExportService importExportService,
                        final ResourceStore resourceStore,
                        final ExportConfig exportConfig) {
        this.importExportService = importExportService;
        this.resourceStore = resourceStore;
        this.exportConfig = exportConfig;
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
        final boolean enabled = exportConfig.isEnabled();
        if (enabled) {
            final ResourceKey tempResourceKey = resourceStore.createTempFile("StroomConfig.zip");

            try {
                final Path tempFile = resourceStore.getTempFile(tempResourceKey);

                final DocRefs docRefs = new DocRefs();
                docRefs.add(ExplorerConstants.ROOT_DOC_REF);

                importExportService.exportConfig(docRefs, tempFile, new ArrayList<>());

                try (final InputStream inputStream = Files.newInputStream(tempFile);
                     final OutputStream outputStream = resp.getOutputStream()) {
                    StreamUtil.streamToStream(inputStream, outputStream);
                }
            } finally {
                resourceStore.deleteTempFile(tempResourceKey);
            }
        } else {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Export is not enabled");
        }
    }
}
