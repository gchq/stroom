/*
 * Copyright 2017 Crown Copyright
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

package stroom.statistics.impl.sql.entity;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.statistics.impl.sql.shared.StatisticResource;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.util.shared.EntityServiceException;

import javax.inject.Inject;

@AutoLogged
class StatisticResourceImpl implements StatisticResource {

    private final StatisticStoreStore statisticStoreStore;
    private final DocumentResourceHelper documentResourceHelper;

    @Inject
    StatisticResourceImpl(final StatisticStoreStore statisticStoreStore,
                          final DocumentResourceHelper documentResourceHelper) {
        this.statisticStoreStore = statisticStoreStore;
        this.documentResourceHelper = documentResourceHelper;
    }

    @Override
    public StatisticStoreDoc fetch(final String uuid) {
        return documentResourceHelper.read(statisticStoreStore, getDocRef(uuid));
    }

    @Override
    public StatisticStoreDoc update(final String uuid, final StatisticStoreDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelper.update(statisticStoreStore, doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(StatisticStoreDoc.DOCUMENT_TYPE)
                .build();
    }
}
