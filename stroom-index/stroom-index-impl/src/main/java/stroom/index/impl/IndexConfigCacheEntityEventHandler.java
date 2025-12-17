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

package stroom.index.impl;

import stroom.docref.DocRef;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexShard;
import stroom.index.shared.LuceneIndexDoc;
import stroom.node.api.NodeInfo;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.util.Optional;

@EntityEventHandler(
        type = LuceneIndexDoc.TYPE,
        action = {EntityAction.DELETE, EntityAction.UPDATE, EntityAction.CLEAR_CACHE})
class IndexConfigCacheEntityEventHandler implements EntityEvent.Handler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexConfigCacheEntityEventHandler.class);

    private final NodeInfo nodeInfo;
    private final LuceneIndexDocCacheImpl indexStructureCache;
    private final IndexShardDao indexShardDao;
    private final IndexShardWriterCache indexShardWriterCache;

    @Inject
    IndexConfigCacheEntityEventHandler(final NodeInfo nodeInfo,
                                       final LuceneIndexDocCacheImpl indexStructureCache,
                                       final IndexShardDao indexShardDao,
                                       final IndexShardWriterCache indexShardWriterCache) {
        this.nodeInfo = nodeInfo;
        this.indexStructureCache = indexStructureCache;
        this.indexShardDao = indexShardDao;
        this.indexShardWriterCache = indexShardWriterCache;
    }

    @Override
    public void onChange(final EntityEvent event) {
        LOGGER.debug("Received event {}", event);
        final EntityAction eventAction = event.getAction();
        final DocRef docRef = event.getDocRef();

        if (LuceneIndexDoc.TYPE.equals(docRef.getType())) {
            switch (eventAction) {
                case CLEAR_CACHE -> indexStructureCache.clear();
                case DELETE -> indexStructureCache.remove(docRef);
                case UPDATE -> {
                    indexStructureCache.remove(docRef);
                    updateIndex(event.getDocRef());
                }
                default -> LOGGER.warn("Unexpected event action {}", eventAction);
            }
        } else {
            LOGGER.warn("Unexpected document type {}", docRef);
        }
    }

    private void updateIndex(final DocRef indexRef) {
        final FindIndexShardCriteria criteria = FindIndexShardCriteria.matchAll();
        criteria.getNodeNameSet().add(nodeInfo.getThisNodeName());
        criteria.getIndexUuidSet().add(indexRef.getUuid());

        final ResultPage<IndexShard> shards = indexShardDao.find(criteria);
        shards.getValues().forEach(shard -> {
            final Optional<IndexShardWriter> optional = indexShardWriterCache.getIfPresent(shard.getId());
            optional.ifPresent(indexShardWriter -> {
                final LuceneIndexDoc index = indexStructureCache.get(indexRef);
                indexShardWriter.setMaxDocumentCount(index.getMaxDocsPerShard());
            });
        });
    }
}
