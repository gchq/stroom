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

package stroom.sqlstore.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.sqlstore.shared.SqlTemporalStoreDoc;
import stroom.sqlstore.shared.SqlTemporalStoreDocResource;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.FetchWithUuid;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
class SqlTemporalStoreDocResourceImpl implements SqlTemporalStoreDocResource, FetchWithUuid<SqlTemporalStoreDoc> {

    private final Provider<SqlTemporalStoreDocStore> sqlStoreDocStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;

    @Inject
    SqlTemporalStoreDocResourceImpl(final Provider<SqlTemporalStoreDocStore> sqlStoreDocStoreProvider,
                            final Provider<DocumentResourceHelper> documentResourceHelperProvider) {
        this.sqlStoreDocStoreProvider = sqlStoreDocStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
    }

    @Override
    public SqlTemporalStoreDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get().read(sqlStoreDocStoreProvider.get(), getDocRef(uuid));
    }

    @Override
    public SqlTemporalStoreDoc update(final String uuid, final SqlTemporalStoreDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelperProvider.get().update(sqlStoreDocStoreProvider.get(), doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(SqlTemporalStoreDoc.TYPE)
                .build();
    }
}
