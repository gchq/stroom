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

package stroom.index.mock;

import stroom.index.impl.IndexShardCreator;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexShardUtil;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.LuceneVersion;
import stroom.index.shared.LuceneVersionUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.io.TempDirProvider;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.file.Files;
import java.nio.file.Path;

@Singleton
public class MockIndexShardCreator implements IndexShardCreator {

    private final IndexShardDao indexShardDao;
    private final TempDirProvider tempDirProvider;
    private final PathCreator pathCreator;

    @Inject
    public MockIndexShardCreator(final TempDirProvider tempDirProvider,
                                 final IndexShardDao indexShardDao) {
        this.tempDirProvider = tempDirProvider;
        this.indexShardDao = indexShardDao;
        pathCreator = new SimplePathCreator(
                () -> tempDirProvider.get().resolve("home"),
                tempDirProvider);
    }

    @Override
    public IndexShard createIndexShard(final IndexShardKey indexShardKey, final String ownerNodeName) {
        final IndexVolume indexVolume = IndexVolume
                .builder()
                .nodeName(ownerNodeName)
                .path(FileUtil.getCanonicalPath(tempDirProvider.get()))
                .build();
        final IndexShard indexShard = indexShardDao.create(
                indexShardKey,
                indexVolume,
                ownerNodeName,
                LuceneVersionUtil.getCurrentVersion());
        final Path indexPath = IndexShardUtil.getIndexPath(indexShard, pathCreator);
        if (Files.isDirectory(indexPath)) {
            FileUtil.deleteContents(indexPath);
        }
        return indexShard;
    }

    @Override
    public void setIndexVersion(final LuceneVersion indexVersion) {

    }
}
