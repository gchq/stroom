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

import stroom.resource.api.ResourceStore;
import stroom.util.io.StreamUtil;
import stroom.util.shared.IsServlet;
import stroom.util.shared.PropertyMap;
import stroom.util.shared.ResourceKey;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.fileupload2.core.DiskFileItem;
import org.apache.commons.fileupload2.core.DiskFileItemFactory;
import org.apache.commons.fileupload2.core.FileUploadException;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serial;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Generic Import Service
 */
public final class ImportFileServlet extends HttpServlet implements IsServlet {

    private static final String FILE_UPLOAD_PROP_NAME = "fileUpload";
    private static final Logger LOGGER = LoggerFactory.getLogger(ImportFileServlet.class);

    @Serial
    private static final long serialVersionUID = 487567988479000995L;

    private static final Set<String> PATH_SPECS = Set.of("/importfile.rpc");

    private final ResourceStore resourceStore;
    private final StreamEventLog streamEventLog;

    @Inject
    ImportFileServlet(final ResourceStore resourceStore,
                      final StreamEventLog streamEventLog) {
        this.resourceStore = resourceStore;
        this.streamEventLog = streamEventLog;
    }

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response)
            throws IOException {
        response.setContentType("text/plain;charset=UTF-8");

        final PropertyMap propertyMap = new PropertyMap();
        propertyMap.setSuccess(false);

        try {
            // Parse the request and populate a map of file items.
            final Map<String, DiskFileItem> items = getFileItems(request);
            if (items.isEmpty()) {
                response.getWriter().write(propertyMap.toArgLine());
                return;
            }

            final DiskFileItem fileItem = items.get(FILE_UPLOAD_PROP_NAME);
            Objects.requireNonNull(fileItem, "Property '" + FILE_UPLOAD_PROP_NAME + "' not found in request");
            final String fileName = fileItem.getName();
            final ResourceKey resourceKey = resourceStore.createTempFile(fileName);
            final Path tempFile = resourceStore.getTempFile(resourceKey);
            streamEventLog.importStream(
                    fileItem,
                    fileName,
                    null);
            try (final InputStream inputStream = fileItem.getInputStream();
                    final OutputStream outputStream = Files.newOutputStream(tempFile)) {
                StreamUtil.streamToStream(inputStream, outputStream);
            }

            propertyMap.setSuccess(true);
            propertyMap.put(ResourceKey.NAME, fileName);
            propertyMap.put(ResourceKey.KEY, resourceKey.getKey());
            fileItem.delete();

        } catch (final RuntimeException e) {
            streamEventLog.importStream(null, null, e);
            LOGGER.error(e.getMessage(), e);
            propertyMap.put("exception", e.getMessage());
        }

        response.getWriter().write(propertyMap.toArgLine());
    }

    private Map<String, DiskFileItem> getFileItems(final HttpServletRequest request) {
        final Map<String, DiskFileItem> fields = new HashMap<>();
        final DiskFileItemFactory factory = DiskFileItemFactory.builder()
                .get();
        final JakartaServletFileUpload<DiskFileItem, DiskFileItemFactory> upload =
                new JakartaServletFileUpload<>(factory);

        try {
            final List<DiskFileItem> items = upload.parseRequest(request);
            for (final DiskFileItem diskFileItem : items) {
                fields.put(diskFileItem.getFieldName(), diskFileItem);
            }
        } catch (final FileUploadException e) {
            LOGGER.error("Unable to get file items!", e);
        }

        return fields;
    }

    /**
     * @return The part of the path that will be in addition to any base path,
     * e.g. "/datafeed".
     */
    @Override
    public Set<String> getPathSpecs() {
        return PATH_SPECS;
    }
}
