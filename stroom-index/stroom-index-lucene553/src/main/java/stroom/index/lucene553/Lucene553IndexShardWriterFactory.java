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

package stroom.index.lucene553;

import stroom.index.impl.IndexConfig;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexShardWriter;
import stroom.index.shared.IndexShard;
import stroom.util.io.PathCreator;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

class Lucene553IndexShardWriterFactory {

    private final IndexShardDao indexShardDao;
    private final Provider<IndexConfig> indexConfigProvider;
    private final PathCreator pathCreator;

    @Inject
    Lucene553IndexShardWriterFactory(final IndexShardDao indexShardDao,
                                     final Provider<IndexConfig> indexConfigProvider,
                                     final PathCreator pathCreator) {
        this.indexShardDao = indexShardDao;
        this.indexConfigProvider = indexConfigProvider;
        this.pathCreator = pathCreator;
    }

    IndexShardWriter create(final IndexShard indexShard,
                            final int maxDocumentCount) {
        return new Lucene553IndexShardWriter(
                indexShardDao,
                indexConfigProvider.get(),
                indexShard,
                pathCreator,
                maxDocumentCount);
    }
}
