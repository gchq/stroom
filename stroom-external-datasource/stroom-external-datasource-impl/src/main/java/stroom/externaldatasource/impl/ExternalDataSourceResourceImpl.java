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

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.externaldatasource.shared.DataColumn;
import stroom.externaldatasource.shared.ExcelSettings;
import stroom.externaldatasource.shared.ExternalDataSourceDoc;
import stroom.externaldatasource.shared.ExternalDataSourceResource;
import stroom.externaldatasource.shared.ExternalDataSourceTestResponse;
import stroom.resource.api.ResourceStore;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;

@AutoLogged
class ExternalDataSourceResourceImpl implements ExternalDataSourceResource {

    private final Provider<ExternalDataSourceStore> storeProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;
    private final Provider<ExternalDataSourceConnectionTester> connectionTesterProvider;
    private final Provider<ResourceStore> resourceStoreProvider;

    /// Creates the instance. Guice supplies every argument.
    @Inject
    ExternalDataSourceResourceImpl(final Provider<ExternalDataSourceStore> storeProvider,
                                   final Provider<DocumentResourceHelper> documentResourceHelperProvider,
                                   final Provider<ExternalDataSourceConnectionTester> connectionTesterProvider,
                                   final Provider<ResourceStore> resourceStoreProvider) {
        this.storeProvider = storeProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
        this.connectionTesterProvider = connectionTesterProvider;
        this.resourceStoreProvider = resourceStoreProvider;
    }

    @Override
    public ExternalDataSourceDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get().read(storeProvider.get(), getDocRef(uuid));
    }

    @Override
    public ExternalDataSourceDoc update(final String uuid, final ExternalDataSourceDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelperProvider.get().update(storeProvider.get(), doc);
    }

    @Override
    @AutoLogged(value = OperationType.PROCESS, verb = "Testing external data source connection")
    public ExternalDataSourceTestResponse testConnection(final ExternalDataSourceDoc doc) {
        return connectionTesterProvider.get().test(doc);
    }

    @Override
    @AutoLogged(value = OperationType.PROCESS, verb = "Detecting the columns of an external data source's data")
    public List<DataColumn> detectColumns(final ExternalDataSourceDoc doc) {
        return connectionTesterProvider.get().detectColumns(doc);
    }

    /// Reading the document checks the user may view it.
    @Override
    @AutoLogged(value = OperationType.EXPORT, verb = "Downloading the workbook of an external data source")
    public ResourceGeneration downloadExcelFile(final String uuid) {
        final ExternalDataSourceDoc doc = documentResourceHelperProvider.get()
                .read(storeProvider.get(), getDocRef(uuid));
        final ExcelSettings excel = doc.getExcel();
        final byte[] data = NullSafe.get(excel, ExcelSettings::getData);
        if (data == null) {
            throw new EntityServiceException("External Data Source '" + doc.getName() + "' has no workbook.");
        }
        final String fileName = NullSafe.isBlankString(excel.getFileName())
                ? ExternalDataSourceStoreImpl.DEFAULT_WORKBOOK_NAME
                : excel.getFileName();
        final ResourceStore resourceStore = resourceStoreProvider.get();
        final ResourceKey resourceKey = resourceStore.createTempFile(fileName);
        try {
            Files.write(resourceStore.getTempFile(resourceKey), data);
        } catch (final IOException e) {
            throw new UncheckedIOException("The workbook could not be prepared for download", e);
        }
        return new ResourceGeneration(resourceKey, List.of());
    }

    private static DocRef getDocRef(final String uuid) {
        return ExternalDataSourceDoc.buildDocRef()
                .uuid(uuid)
                .build();
    }
}
