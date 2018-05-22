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

package stroom.index;

import stroom.entity.event.EntityEvent;
import stroom.entity.event.EntityEventHandler;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexShard;
import stroom.node.NodeCache;
import stroom.node.shared.Node;
import stroom.query.api.v2.DocRef;

import javax.inject.Inject;
import java.util.List;

@EntityEventHandler(type = IndexDoc.DOCUMENT_TYPE)
class IndexConfigCacheEntityEventHandler implements EntityEvent.Handler {
    private final NodeCache nodeCache;
    private final IndexConfigCacheImpl indexConfigCache;
    private final IndexShardService indexShardService;
    private final IndexShardWriterCache indexShardWriterCache;

    @Inject
    IndexConfigCacheEntityEventHandler(final NodeCache nodeCache,
                                       final IndexConfigCacheImpl indexConfigCache,
                                       final IndexShardService indexShardService,
                                       final IndexShardWriterCache indexShardWriterCache) {
        this.nodeCache = nodeCache;
        this.indexConfigCache = indexConfigCache;
        this.indexShardService = indexShardService;
        this.indexShardWriterCache = indexShardWriterCache;
    }

    @Override
    public void onChange(final EntityEvent event) {
        if (IndexDoc.DOCUMENT_TYPE.equals(event.getDocRef().getType())) {
            indexConfigCache.remove(event.getDocRef());
            updateIndex(event.getDocRef());
        }
    }

    private void updateIndex(final DocRef indexRef) {
        final FindIndexShardCriteria criteria = new FindIndexShardCriteria();
        criteria.getNodeIdSet().add(nodeCache.getDefaultNode());
        criteria.getFetchSet().add(IndexDoc.DOCUMENT_TYPE);
        criteria.getFetchSet().add(Node.ENTITY_TYPE);
        criteria.getIndexSet().add(indexRef);

        final List<IndexShard> shards = indexShardService.find(criteria);
        shards.forEach(shard -> {
            final IndexShardWriter indexShardWriter = indexShardWriterCache.getWriterByShardId(shard.getId());
            if (indexShardWriter != null) {
                final IndexConfig indexConfig = indexConfigCache.get(indexRef);
                indexShardWriter.updateIndexConfig(indexConfig);
            }
        });
    }
}
