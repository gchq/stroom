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
import stroom.proxy.repo.queue.Batch;
import stroom.proxy.repo.queue.BatchUtil;

import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

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
        BatchUtil.transfer(sourceItems::getNewSourceItems, this::addItems);
    }

    void addItems(final Batch<RepoSourceItemRef> newSourceItems) {
        final AggregatorConfig aggregatorConfig = aggregatorConfigProvider.get();
        aggregateDao.addItems(newSourceItems,
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
        boolean full = true;
        final int batchSize = dbConfig.getBatchSize();
        while (full) {
            final long count = aggregateDao.closeAggregates(
                    maxItemsPerAggregate,
                    maxUncompressedByteSize,
                    maxAggregateAgeMs,
                    batchSize);
            progressLog.add("Aggregator - closeOldAggregates", count);
            full = count == batchSize;
        }
    }

    public Batch<Aggregate> getCompleteAggregates() {
        return aggregateDao.getNewAggregates();
    }

    public Batch<Aggregate> getCompleteAggregates(final long timeout,
                                                  final TimeUnit timeUnit) {
        return aggregateDao.getNewAggregates(timeout, timeUnit);
    }

    public void clear() {
        aggregateDao.clear();
    }
}
