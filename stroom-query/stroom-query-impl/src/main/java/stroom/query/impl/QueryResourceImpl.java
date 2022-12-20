/*
 * Copyright 2022 Crown Copyright
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

package stroom.query.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.query.shared.QueryDoc;
import stroom.query.shared.QueryResource;
import stroom.util.shared.EntityServiceException;

import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged
class QueryResourceImpl implements QueryResource {

    private final Provider<QueryStore> queryStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;

    @Inject
    QueryResourceImpl(final Provider<QueryStore> queryStoreProvider,
                      final Provider<DocumentResourceHelper> documentResourceHelperProvider) {
        this.queryStoreProvider = queryStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
    }

    @Override
    public QueryDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get().read(queryStoreProvider.get(), getDocRef(uuid));
    }

    @Override
    public QueryDoc update(final String uuid, final QueryDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelperProvider.get().update(queryStoreProvider.get(), doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(QueryDoc.DOCUMENT_TYPE)
                .build();
    }
}
