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

package stroom.sqldatasource.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.sqldatasource.shared.SqlDataSourceDoc;
import stroom.sqldatasource.shared.SqlDataSourceResource;
import stroom.sqldatasource.shared.SqlDataSourceTestResponse;
import stroom.util.shared.EntityServiceException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
class SqlDataSourceResourceImpl implements SqlDataSourceResource {

    private final Provider<SqlDataSourceStore> storeProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;
    private final Provider<SqlDataSourceConnectionTester> connectionTesterProvider;

    /// Creates the instance. Guice supplies every argument.
    @Inject
    SqlDataSourceResourceImpl(final Provider<SqlDataSourceStore> storeProvider,
                              final Provider<DocumentResourceHelper> documentResourceHelperProvider,
                              final Provider<SqlDataSourceConnectionTester> connectionTesterProvider) {
        this.storeProvider = storeProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
        this.connectionTesterProvider = connectionTesterProvider;
    }

    @Override
    public SqlDataSourceDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get().read(storeProvider.get(), getDocRef(uuid));
    }

    @Override
    public SqlDataSourceDoc update(final String uuid, final SqlDataSourceDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelperProvider.get().update(storeProvider.get(), doc);
    }

    @Override
    @AutoLogged(value = OperationType.PROCESS, verb = "Testing SQL data source connection")
    public SqlDataSourceTestResponse testConnection(final SqlDataSourceDoc doc) {
        return connectionTesterProvider.get().test(doc);
    }

    private static DocRef getDocRef(final String uuid) {
        return SqlDataSourceDoc.buildDocRef()
                .uuid(uuid)
                .build();
    }
}
