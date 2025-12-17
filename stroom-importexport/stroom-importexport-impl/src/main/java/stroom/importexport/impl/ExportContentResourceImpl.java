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

package stroom.importexport.impl;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.importexport.api.ContentService;
import stroom.resource.api.ResourceStore;
import stroom.util.io.StreamUtil;
import stroom.util.shared.ResourceKey;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@AutoLogged
public class ExportContentResourceImpl implements ExportContentResource {

    private final Provider<ContentService> contentServiceProvider;
    private final Provider<ResourceStore> resourceStoreProvider;

    @Inject
    public ExportContentResourceImpl(final Provider<ContentService> contentServiceProvider,
                                     final Provider<ResourceStore> resourceStoreProvider) {
        this.contentServiceProvider = contentServiceProvider;
        this.resourceStoreProvider = resourceStoreProvider;
    }

    @Override
    @AutoLogged(value = OperationType.EXPORT, verb = "Exporting All Config")
    public Response export() {

        final ResourceKey tempResourceKey = contentServiceProvider.get().exportAll();
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
                .header("Content-Disposition", "attachment; filename=\"" + tempResourceKey.getName() + "\"")
                .build();
    }
}
