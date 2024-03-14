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

import stroom.proxy.repo.dao.lmdb.AggregateDao;
import stroom.proxy.repo.dao.db.ProxyDbConfig;
import stroom.proxy.repo.dao.lmdb.AggregateKey;
import stroom.proxy.repo.queue.Batch;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.checkerframework.checker.units.qual.Time;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Singleton
public class Aggregator {

    private final RepoSourceItems sourceItems;
    private final AggregateDao aggregateDao;
    private final Provider<AggregatorConfig> aggregatorConfigProvider;
    private final ProgressLog progressLog;
    private final ProxyDbConfig dbConfig;

    @Inject
    Aggregator(final RepoSourceItems sourceItems,
               final AggregateDao aggregateDao,
               final Provider<AggregatorConfig> aggregatorConfigProvider,
               final ProgressLog progressLog,
               final ProxyDbConfig dbConfig) {
        this.sourceItems = sourceItems;
        this.aggregateDao = aggregateDao;
        this.aggregatorConfigProvider = aggregatorConfigProvider;
        this.progressLog = progressLog;
        this.dbConfig = dbConfig;
    }

    public synchronized void aggregateAll() {
        Optional<RepoSourceItemRef> optional = sourceItems.getNextSourceItem(0, TimeUnit.MILLISECONDS);
        while (optional.isPresent()) {
            addItem(optional.get());
            optional = sourceItems.getNextSourceItem(0, TimeUnit.MILLISECONDS);
        }
    }

    public void addItem(final RepoSourceItemRef newSourceItem) {
        final AggregatorConfig aggregatorConfig = aggregatorConfigProvider.get();
        aggregateDao.addItem(newSourceItem,
                aggregatorConfig.getMaxItemsPerAggregate(),
                aggregatorConfig.getMaxUncompressedByteSize());
    }

    public void closeOldAggregates() {
        final AggregatorConfig aggregatorConfig = aggregatorConfigProvider.get();
        closeOldAggregates(
                aggregatorConfig.getMaxItemsPerAggregate(),
                aggregatorConfig.getMaxUncompressedByteSize(),
                aggregatorConfig.getMaxAggregateAge().toMillis());
    }

    public void closeOldAggregates(final int maxItemsPerAggregate,
                                   final long maxUncompressedByteSize,
                                   final long maxAggregateAgeMs) {
        aggregateDao.closeAggregates(
                    maxItemsPerAggregate,
                    maxUncompressedByteSize,
                    maxAggregateAgeMs);
    }

    public AggregateKey getCompleteAggregates() {
        return aggregateDao.getNewAggregate();
    }

//    public Batch<Aggregate> getCompleteAggregates(final long timeout,
//                                                  final TimeUnit timeUnit) {
//        return aggregateDao.getNewAggregates(timeout, timeUnit);
//    }

    public void clear() {
        aggregateDao.clear();
    }
}
