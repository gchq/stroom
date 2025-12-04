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

import stroom.index.shared.IndexShardKey;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Pool API into open index shards.
 */
@Singleton
class IndexerImpl implements Indexer {

    private final ActiveShardsCache activeShardsCache;


    @Inject
    IndexerImpl(final ActiveShardsCache activeShardsCache) {
        this.activeShardsCache = activeShardsCache;
    }

    @Override
    public void addDocument(final IndexShardKey indexShardKey, final IndexDocument document) {
        if (document != null) {
            activeShardsCache.get(indexShardKey).addDocument(document);
        }
    }
}
