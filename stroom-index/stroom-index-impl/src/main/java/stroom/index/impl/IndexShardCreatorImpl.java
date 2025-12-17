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
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.LuceneVersion;
import stroom.index.shared.LuceneVersionUtil;
import stroom.index.shared.Partition;
import stroom.security.api.SecurityContext;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.nio.file.Files;
import java.nio.file.Path;

public class IndexShardCreatorImpl implements IndexShardCreator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardCreatorImpl.class);

    private final LuceneIndexDocCache luceneIndexDocCache;
    private final IndexShardDao indexShardDao;
    private final IndexVolumeService indexVolumeService;
    private final PathCreator pathCreator;
    private final SecurityContext securityContext;

    private LuceneVersion indexVersion = LuceneVersionUtil.CURRENT_LUCENE_VERSION;

    @Inject
    IndexShardCreatorImpl(final LuceneIndexDocCache indexStructureCache,
                          final IndexShardDao indexShardDao,
                          final IndexVolumeService indexVolumeService,
                          final PathCreator pathCreator,
                          final SecurityContext securityContext) {
        this.luceneIndexDocCache = indexStructureCache;
        this.indexShardDao = indexShardDao;
        this.indexVolumeService = indexVolumeService;
        this.pathCreator = pathCreator;
        this.securityContext = securityContext;
    }


    @Override
    public IndexShard createIndexShard(final IndexShardKey indexShardKey,
                                       final String ownerNodeName) {
        return securityContext.asProcessingUserResult(() -> {
            final LuceneIndexDoc index = luceneIndexDocCache.get(
                    new DocRef(LuceneIndexDoc.TYPE, indexShardKey.getIndexUuid()));
            final String volGroupName = index.getVolumeGroupName();
            final IndexVolume indexVolume = indexVolumeService.selectVolume(volGroupName, ownerNodeName);

            // Test the validity of the volume path.
            final Path path = pathCreator.toAppPath(indexVolume.getPath());
            if (!Files.isDirectory(path)) {
                throw new RuntimeException("Index volume path not found: " + indexVolume.getPath());
            }

            final IndexShard indexShard = indexShardDao.create(
                    indexShardKey,
                    indexVolume,
                    ownerNodeName,
                    indexVersion.getDisplayValue());

            LOGGER.info("Created index shard for index: {} ({}), partition: '{}', ownerNodeName: {}, " +
                        "index version: {}, volume group: '{}', volume state: {}, path: {}",
                    index.getName(),
                    index.getUuid(),
                    NullSafe.get(indexShardKey.getPartition(), Partition::getLabel),
                    ownerNodeName,
                    indexShard.getIndexVersion(),
                    volGroupName,
                    indexVolume.getState(),
                    path);

            return indexShard;
        });
    }

    @Override
    public void setIndexVersion(final LuceneVersion indexVersion) {
        this.indexVersion = indexVersion;
    }
}
