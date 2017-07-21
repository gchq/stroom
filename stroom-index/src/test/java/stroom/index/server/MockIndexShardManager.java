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
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexShardService;
import stroom.util.shared.ModelStringUtil;
import stroom.util.spring.StroomSpringProfiles;

import javax.inject.Inject;
import java.util.List;
import java.util.Set;

@Profile(StroomSpringProfiles.TEST)
@Component("indexShardManager")
public class MockIndexShardManager implements IndexShardManager {
//    private final IndexShardService indexShardService;
//    private final IndexShardWriterCache indexShardWriterCache;
//
//    @Inject
//    MockIndexShardManager(final IndexShardService indexShardService, final IndexShardWriterCache indexShardWriterCache) {
//        this.indexShardService = indexShardService;
//        this.indexShardWriterCache = indexShardWriterCache;
//    }

    @Override
    public Long findFlush(final FindIndexShardCriteria criteria) {
        return null;
    }

    @Override
    public Long findDelete(final FindIndexShardCriteria criteria) {
        return null;
    }

    @Override
    public IndexShard load(final IndexShard indexShard) {
        return indexShard;
    }

    @Override
    public void setStatus(final long indexShardId, final IndexShardStatus status) {
//        synchronized(this) {
//            final IndexShard indexShard = indexShardService.loadById(indexShardId);
//            indexShard.setStatus(status);
//            indexShardService.save(indexShard);
//        }
    }

    @Override
    public void update(final long indexShardId, final Integer documentCount, final Long commitDurationMs, final Long commitMs, final Long fileSize) {
//        synchronized(this) {
//            final IndexShard indexShard = indexShardService.loadById(indexShardId);
//
//            if (documentCount != null) {
//                indexShard.setDocumentCount(documentCount);
//                indexShard.setCommitDocumentCount(documentCount - indexShard.getDocumentCount());
//            }
//            if (commitDurationMs != null) {
//                indexShard.setCommitDurationMs(commitDurationMs);
//            }
//            if (commitMs != null) {
//                indexShard.setCommitMs(commitMs);
//            }
//            if (fileSize != null) {
//                indexShard.setFileSize(fileSize);
//            }
//
//            indexShardService.save(indexShard);
//        }
    }

    @Override
    public void checkRetention() {
    }

    @Override
    public void deleteFromDisk() {
    }

    @Override
    public void shutdown() {
    }
}
