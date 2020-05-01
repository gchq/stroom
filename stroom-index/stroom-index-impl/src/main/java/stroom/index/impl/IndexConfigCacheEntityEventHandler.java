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
 *
 */

package stroom.index.impl;

import stroom.docref.DocRef;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexShard;
import stroom.node.api.NodeInfo;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;

@EntityEventHandler(type = IndexDoc.DOCUMENT_TYPE)
class IndexConfigCacheEntityEventHandler implements EntityEvent.Handler {
    private final NodeInfo nodeInfo;
    private final IndexStructureCacheImpl indexStructureCache;
    private final IndexShardService indexShardService;
    private final IndexShardWriterCache indexShardWriterCache;

    @Inject
    IndexConfigCacheEntityEventHandler(final NodeInfo nodeInfo,
                                       final IndexStructureCacheImpl indexStructureCache,
                                       final IndexShardService indexShardService,
                                       final IndexShardWriterCache indexShardWriterCache) {
        this.nodeInfo = nodeInfo;
        this.indexStructureCache = indexStructureCache;
        this.indexShardService = indexShardService;
        this.indexShardWriterCache = indexShardWriterCache;
    }

    @Override
    public void onChange(final EntityEvent event) {
        if (IndexDoc.DOCUMENT_TYPE.equals(event.getDocRef().getType())) {
            indexStructureCache.remove(event.getDocRef());
            updateIndex(event.getDocRef());
        }
    }

    private void updateIndex(final DocRef indexRef) {
        final FindIndexShardCriteria criteria = FindIndexShardCriteria.matchAll();
        criteria.getNodeNameSet().add(nodeInfo.getThisNodeName());
        criteria.getIndexUuidSet().add(indexRef.getUuid());

        final ResultPage<IndexShard> shards = indexShardService.find(criteria);
        shards.getValues().forEach(shard -> {
            final IndexShardWriter indexShardWriter = indexShardWriterCache.getWriterByShardId(shard.getId());
            if (indexShardWriter != null) {
                final IndexStructure indexStructure = indexStructureCache.get(indexRef);
                indexShardWriter.updateIndexStructure(indexStructure);
            }
        });
    }
}
