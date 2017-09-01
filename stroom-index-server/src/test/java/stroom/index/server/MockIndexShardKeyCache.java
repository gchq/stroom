/*
 * Copyright 2016 Crown Copyright
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

package stroom.index.server;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.util.spring.StroomSpringProfiles;

import javax.inject.Inject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Profile(StroomSpringProfiles.TEST)
@Component("indexShardKeyCache")
public class MockIndexShardKeyCache implements IndexShardKeyCache {
    private final MockIndexShardService indexShardService;
    private Map<IndexShardKey, IndexShard> indexShardMap = new ConcurrentHashMap<>();

    @Inject
    MockIndexShardKeyCache(final MockIndexShardService indexShardService) {
        this.indexShardService = indexShardService;
    }

    @Override
    public IndexShard getOrCreate(final IndexShardKey key) {
        return indexShardMap.computeIfAbsent(key, k -> indexShardService.createIndexShard(k, null));
    }

    @Override
    public void remove(final IndexShardKey key) {
        indexShardMap.remove(key);
    }

    @Override
    public void clear() {
        indexShardMap.clear();
    }
}
