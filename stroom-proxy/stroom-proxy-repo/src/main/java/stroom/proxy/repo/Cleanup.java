/*
 * Copyright 2019 Crown Copyright
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

package stroom.proxy.repo;

import stroom.proxy.repo.dao.AggregateDao;
import stroom.proxy.repo.dao.ForwardAggregateDao;
import stroom.proxy.repo.dao.ForwardSourceDao;
import stroom.proxy.repo.dao.SourceDao;
import stroom.proxy.repo.dao.SourceItemDao;
import stroom.proxy.repo.store.SequentialFileStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class Cleanup {

    private static final Logger LOGGER = LoggerFactory.getLogger(Cleanup.class);

    private final SourceDao sourceDao;
    private final SourceItemDao sourceItemDao;
    private final AggregateDao aggregateDao;
    private final ForwardSourceDao forwardSourceDao;
    private final ForwardAggregateDao forwardAggregateDao;
    private final ProxyDbConfig dbConfig;

    private final RepoSources sources;
    private final SequentialFileStore sequentialFileStore;

    @Inject
    Cleanup(final RepoSources sources,
            final SourceDao sourceDao,
            final SourceItemDao sourceItemDao,
            final AggregateDao aggregateDao,
            final ForwardSourceDao forwardSourceDao,
            final ForwardAggregateDao forwardAggregateDao,
            final ProxyDbConfig dbConfig,
            final SequentialFileStore sequentialFileStore) {
        this.sources = sources;
        this.sourceDao = sourceDao;
        this.sourceItemDao = sourceItemDao;
        this.aggregateDao = aggregateDao;
        this.forwardSourceDao = forwardSourceDao;
        this.forwardAggregateDao = forwardAggregateDao;
        this.dbConfig = dbConfig;
        this.sequentialFileStore = sequentialFileStore;
    }

    public void cleanupSources() {
        // Mark sources as being ready for deletion.
        sources.markDeletableSources();

        // Now delete files by getting batches of deleted items and removing the files from the store.
        long minSourceId = -1;
        final int batchSize = 100_000;
        boolean success = true;
        while (success) {
            final List<RepoSource> list = sources.getDeletableSources(minSourceId, batchSize);
            success = list.size() > 0;
            for (final RepoSource source : list) {
                try {
                    sequentialFileStore.deleteSource(source.fileStoreId());
                    minSourceId = Math.max(minSourceId, source.id());
                } catch (final IOException e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }
        }

        // Finally delete the items we have marked for deletion.
        sources.deleteSources();
    }

    public void resetAggregateForwarder() {
        forwardAggregateDao.clear();
        sourceItemDao.clear();
        aggregateDao.clear();
        sourceDao.resetExamined();
    }

    public void resetSourceForwarder() {
        forwardSourceDao.clear();
    }
}
