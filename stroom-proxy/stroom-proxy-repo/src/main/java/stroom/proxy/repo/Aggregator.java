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
import stroom.proxy.repo.dao.SourceEntryDao;
import stroom.proxy.repo.dao.SourceEntryDao.SourceItem;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class Aggregator {

    private static final int BATCH_SIZE = 1000000;

    private final SourceEntryDao sourceEntryDao;
    private final AggregateDao aggregateDao;
    private final AggregatorConfig config;

    private final List<ChangeListener> listeners = new CopyOnWriteArrayList<>();

    private long lastClosedAggregates;
    private volatile boolean firstRun = true;

    @Inject
    Aggregator(final SourceEntryDao sourceEntryDao,
               final AggregateDao aggregateDao,
               final AggregatorConfig config) {
        this.sourceEntryDao = sourceEntryDao;
        this.aggregateDao = aggregateDao;
        this.config = config;
    }

    public synchronized void aggregate() {
        // Start by trying to close old aggregates.
        closeOldAggregates();

        boolean run = true;
        while (run) {
            final List<SourceItem> sourceItems = sourceEntryDao.getNewSourceItems(BATCH_SIZE);
            sourceItems.forEach(this::addItem);

            // Stop aggregating if the last query did not return a result as big as the batch size.
            if (sourceItems.size() < BATCH_SIZE || Thread.currentThread().isInterrupted()) {
                run = false;
            }
        }
    }

    public synchronized void aggregate(final long sourceId) {
        // Start by trying to close old aggregates.
        closeOldAggregates();

        final List<SourceItem> sourceItems = sourceEntryDao.getNewSourceItemsForSource(sourceId);
        sourceItems.forEach(this::addItem);
    }

    synchronized int addItem(final SourceItem sourceItem) {
        aggregateDao.addItem(
                sourceItem,
                config.getMaxUncompressedByteSize(),
                config.getMaxItemsPerAggregate());

        // Close any old aggregates.
        return closeOldAggregates();
    }

    synchronized int closeOldAggregates() {
        final long now = System.currentTimeMillis();
        if (now > lastClosedAggregates + config.getAggregationFrequency().toMillis()) {
            lastClosedAggregates = now;

            final long oldest = now - config.getMaxAggregateAge().toMillis();
            return closeOldAggregates(oldest);
        }
        return 0;
    }

    public synchronized int closeOldAggregates(final long oldest) {
        final int count = aggregateDao.closeAggregates(
                config.getMaxItemsPerAggregate(),
                config.getMaxUncompressedByteSize(),
                oldest
        );

        // If we have closed some aggregates then let others know there are some available.
        if (count > 0 || firstRun) {
            firstRun = false;
            fireChange(count);
        }

        return count;
    }

    private void fireChange(final int count) {
        listeners.forEach(listener -> listener.onChange(count));
    }

    public void addChangeListener(final ChangeListener changeListener) {
        listeners.add(changeListener);
    }

    public void clear() {
        aggregateDao.clear();
    }

    public interface ChangeListener {

        void onChange(int count);
    }
}
