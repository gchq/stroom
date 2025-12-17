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

package stroom.statistics.impl.hbase.entity;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.statistics.impl.hbase.shared.StatsStoreResource;
import stroom.statistics.impl.hbase.shared.StroomStatsStoreDoc;
import stroom.util.shared.EntityServiceException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@AutoLogged
@Singleton
class StatsStoreResourceImpl implements StatsStoreResource {

    private final Provider<StroomStatsStoreStore> stroomStatsStoreStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;

    @Inject
    StatsStoreResourceImpl(final Provider<StroomStatsStoreStore> stroomStatsStoreStoreProvider,
                           final Provider<DocumentResourceHelper> documentResourceHelperProvider) {
        this.stroomStatsStoreStoreProvider = stroomStatsStoreStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
    }

    @Override
    public StroomStatsStoreDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get().read(stroomStatsStoreStoreProvider.get(), getDocRef(uuid));
    }

    @Override
    public StroomStatsStoreDoc update(final String uuid, final StroomStatsStoreDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelperProvider.get().update(stroomStatsStoreStoreProvider.get(), doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(StroomStatsStoreDoc.TYPE)
                .build();
    }
}
