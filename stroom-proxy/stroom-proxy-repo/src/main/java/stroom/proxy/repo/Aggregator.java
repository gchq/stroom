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
import stroom.util.concurrent.StripedLock;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class Aggregator {

    private final RepoSourceItems sourceItems;
    private final AggregateDao aggregateDao;
    private final Provider<AggregatorConfig> aggregatorConfigProvider;
    private final ProgressLog progressLog;
    private final StripedLock stripedLock = new StripedLock();

    @Inject
    Aggregator(final RepoSourceItems sourceItems,
               final AggregateDao aggregateDao,
               final Provider<AggregatorConfig> aggregatorConfigProvider,
               final ProgressLog progressLog) {
        this.sourceItems = sourceItems;
        this.aggregateDao = aggregateDao;
        this.aggregatorConfigProvider = aggregatorConfigProvider;
        this.progressLog = progressLog;
    }

    public void aggregateAll() {
        QueueUtil.consumeAll(() -> sourceItems.getNewSourceItem(0, TimeUnit.MILLISECONDS),
                this::addItem);
    }

    public void aggregateNext() {
        sourceItems.getNewSourceItem().ifPresent(this::addItem);
    }

    void addItem(final RepoSourceItemRef sourceItem) {
        // Only deal with finding an aggregate that is the right fit for the feed and type name one at a time.
        final AggregateKey aggregateKey = new AggregateKey(sourceItem.getFeedName(), sourceItem.getTypeName());
        final Lock lock = stripedLock.getLockForKey(aggregateKey);
        try {
            lock.lockInterruptibly();
            try {
                final AggregatorConfig aggregatorConfig = aggregatorConfigProvider.get();
                aggregateDao.addItem(
                        sourceItem,
                        aggregatorConfig.getMaxItemsPerAggregate(),
                        aggregatorConfig.getMaxUncompressedByteSize());
            } finally {
                lock.unlock();
            }
        } catch (final InterruptedException e) {
            // Continue to interrupt.
            Thread.currentThread().interrupt();
        }
        progressLog.increment("Aggregator - addItem");
    }

    public void closeOldAggregates() {
        final long now = System.currentTimeMillis();
        final long oldest = now - aggregatorConfigProvider.get().getMaxAggregateAge().toMillis();
        closeOldAggregates(oldest);
    }

    public void closeOldAggregates(final long oldest) {
        final AggregatorConfig aggregatorConfig = aggregatorConfigProvider.get();
        final List<Aggregate> aggregates = aggregateDao.getClosableAggregates(
                aggregatorConfig.getMaxItemsPerAggregate(),
                aggregatorConfig.getMaxUncompressedByteSize(),
                oldest
        );

        progressLog.add("Aggregator - closeOldAggregates", aggregates.size());
        for (final Aggregate aggregate : aggregates) {
            final AggregateKey aggregateKey = new AggregateKey(aggregate.getFeedName(), aggregate.getTypeName());
            final Lock lock = stripedLock.getLockForKey(aggregateKey);
            try {
                lock.lockInterruptibly();
                try {
                    aggregateDao.closeAggregate(aggregate);
                } finally {
                    lock.unlock();
                }
            } catch (final InterruptedException e) {
                // Continue to interrupt.
                Thread.currentThread().interrupt();
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    public Map<RepoSource, List<RepoSourceItem>> getItems(final long aggregateId) {
        return aggregateDao.fetchSourceItems(aggregateId);
    }

    public Optional<Aggregate> getCompleteAggregate() {
        return aggregateDao.getNewAggregate();
    }

    public Optional<Aggregate> getCompleteAggregate(final long timeout,
                                                    final TimeUnit timeUnit) {
        return aggregateDao.getNewAggregate(timeout, timeUnit);
    }

    public void clear() {
        aggregateDao.clear();
    }

    private static class AggregateKey {

        private final String feedName;
        private final String typeName;

        public AggregateKey(final String feedName, final String typeName) {
            this.feedName = feedName;
            this.typeName = typeName;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final AggregateKey that = (AggregateKey) o;
            return Objects.equals(feedName, that.feedName) && Objects.equals(typeName, that.typeName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(feedName, typeName);
        }
    }
}
