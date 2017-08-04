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

package stroom.index.server;

import org.springframework.stereotype.Component;
import stroom.entity.server.event.EntityEvent;
import stroom.entity.server.event.EntityEventHandler;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.Index;
import stroom.index.shared.IndexShard;
import stroom.node.server.NodeCache;
import stroom.node.shared.Node;
import stroom.query.api.v1.DocRef;

import javax.inject.Inject;
import java.util.List;

@Component
@EntityEventHandler(type = Index.ENTITY_TYPE)
public class IndexConfigCacheEntityEventHandler implements EntityEvent.Handler {
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
        if (Index.ENTITY_TYPE.equals(event.getDocRef().getType())) {
            indexConfigCache.remove(event.getDocRef());
            updateIndex(event.getDocRef());
        }
    }

    private void updateIndex(final DocRef indexRef) {
        final FindIndexShardCriteria criteria = new FindIndexShardCriteria();
        criteria.getNodeIdSet().add(nodeCache.getDefaultNode());
        criteria.getFetchSet().add(Index.ENTITY_TYPE);
        criteria.getFetchSet().add(Node.ENTITY_TYPE);
        criteria.getIndexIdSet().add(indexRef.getId());

        final List<IndexShard> shards = indexShardService.find(criteria);
        shards.forEach(shard -> {
            final IndexShardWriter indexShardWriter = indexShardWriterCache.getQuiet(shard.getId());
            if (indexShardWriter != null) {
                final IndexConfig indexConfig = indexConfigCache.getOrCreate(indexRef);
                indexShardWriter.updateIndexConfig(indexConfig);
            }
        });
    }
}
