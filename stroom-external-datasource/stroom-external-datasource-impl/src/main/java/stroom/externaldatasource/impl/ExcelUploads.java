/*
 * Copyright 2026 Crown Copyright
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

package stroom.externaldatasource.impl;

import stroom.resource.api.ResourceStore;
import stroom.util.io.ByteSize;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.ResourceKey;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/// Reads Excel workbooks the user has uploaded but not yet saved with a document.
///
/// Uploads are held by the [ResourceStore] under a key that only the user who uploaded the file can
/// read.
@Singleton
class ExcelUploads {

    private final Provider<ResourceStore> resourceStoreProvider;
    private final Provider<ExternalDataSourceConfig> configProvider;

    /// Creates the instance. Guice supplies every argument.
    @Inject
    ExcelUploads(final Provider<ResourceStore> resourceStoreProvider,
                 final Provider<ExternalDataSourceConfig> configProvider) {
        this.resourceStoreProvider = resourceStoreProvider;
        this.configProvider = configProvider;
    }

    /// Reads an uploaded workbook, refusing one over the stored data limit before reading it.
    ///
    /// @param uploadKey The key the upload servlet gave the file.
    /// @return The workbook.
    /// @throws EntityServiceException If there is no such upload for the current user, or it is too large.
    byte[] read(final ResourceKey uploadKey) {
        final Path path = resourceStoreProvider.get().getTempFile(uploadKey);
        if (path == null || !Files.isRegularFile(path)) {
            throw new EntityServiceException("The uploaded workbook could not be found. Upload it again.");
        }
        try {
            final ByteSize limit = configProvider.get().getMaxStoredDataSize();
            if (Files.size(path) > limit.getBytes()) {
                throw new EntityServiceException("The workbook is more than the limit of " + limit
                                                 + " set by the property "
                                                 + "stroom.externalDataSource.maxStoredDataSize.");
            }
            return Files.readAllBytes(path);
        } catch (final IOException e) {
            throw new UncheckedIOException("The uploaded workbook could not be read", e);
        }
    }

    /// Removes an uploaded workbook once it has been saved with its document.
    ///
    /// @param uploadKey The key the upload servlet gave the file.
    void delete(final ResourceKey uploadKey) {
        resourceStoreProvider.get().deleteTempFile(uploadKey);
    }
}
