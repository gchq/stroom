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

package stroom.query;

import stroom.mapreduce.Pair;
import stroom.mapreduce.PairQueue;
import stroom.mapreduce.Reader;
import stroom.mapreduce.Source;
import stroom.mapreduce.UnsafePairQueue;
import stroom.node.shared.ClientProperties;
import stroom.query.Items.RemoveHandler;
import stroom.query.shared.Field;
import stroom.util.config.StroomProperties;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.HasTerminate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class TablePayloadHandler implements PayloadHandler {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(TablePayloadHandler.class);

    private static class ResultStoreCreator implements Reader<String, Item> {
        private final CompiledSorter sorter;
        private final Map<String, Items<Item>> childMap;

        public ResultStoreCreator(final CompiledSorter sorter) {
            this.sorter = sorter;
            childMap = new HashMap<>();
        }

        public ResultStore create(final long size, final long totalSize) {
            return new ResultStore(childMap, size, totalSize);
        }

        @Override
        public void read(final Source<String, Item> source) {
            // We should now have a reduction in the reducedQueue.
            for (final Pair<String, Item> pair : source) {
                final Item item = pair.getValue();
                final Items<Item> items = getItems(childMap, item.parentKey, item.depth);
                items.add(item);
            }
        }

        private Items<Item> getItems(final Map<String, Items<Item>> childMap, final String parentKey, final int depth) {
            Items<Item> children = childMap.get(parentKey);
            if (children == null) {
                children = new ItemsArrayList<>();
                childMap.put(parentKey, children);
            }

            return children;
        }

        public void trim(final int[] sizes) {
            trim(sizes, null, 0);
        }

        private void trim(final int[] sizes, final String parentKey, final int depth) {
            int size = sizes[sizes.length - 1];
            if (depth < sizes.length) {
                size = sizes[depth];
            }

            final Items<Item> parentItems = childMap.get(parentKey);
            if (parentItems != null) {
                parentItems.trim(size, sorter, new RemoveHandler<Item>() {
                    @Override
                    public void onRemove(final Item item) {
                        // If there is a group key then cascade removal.
                        if (item.groupKey != null) {
                            remove(item.groupKey);
                        }
                    }
                });

                // Ensure remaining items children are also trimmed by cascading
                // trim operation.

                // Lower levels of results should be reduced by increasing
                // amounts so that we don't get an exponential number of
                // results.
                // int sz = size / 10;
                // if (sz < 1) {
                // sz = 1;
                // }
                for (final Item item : parentItems) {
                    if (item.groupKey != null) {
                        trim(sizes, item.groupKey, depth + 1);
                    }
                }
            }
        }

        private void remove(final String parentKey) {
            final Items<Item> items = childMap.get(parentKey);
            if (items != null) {
                childMap.remove(parentKey);

                // Cascade delete.
                for (final Item item : items) {
                    if (item.groupKey != null) {
                        remove(item.groupKey);
                    }
                }
            }
        }
    }

    private final CompiledSorter compiledSorter;
    private final CompiledDepths compiledDepths;
    private final int[] storeTrimSizes;
    private volatile PairQueue<String, Item> currentQueue;
    private volatile ResultStore resultStore;
    private final AtomicLong totalResults = new AtomicLong();

    private final LinkedBlockingQueue<UnsafePairQueue<String, Item>> pendingMerges = new LinkedBlockingQueue<>();
    private final AtomicBoolean merging = new AtomicBoolean();

    public TablePayloadHandler(final List<Field> fields, final boolean showDetails, final int[] storeTrimSizes) {
        this.compiledSorter = new CompiledSorter(fields);
        this.compiledDepths = new CompiledDepths(fields, showDetails);
        this.storeTrimSizes = getStoreTrimSizes(storeTrimSizes);
    }

    private int[] getStoreTrimSizes(final int[] storeTrimSizes) {
        int[] defaultArray = null;

        try {
            final String value = StroomProperties.getProperty(ClientProperties.MAX_RESULTS);
            if (value != null) {
                final String[] parts = value.split(",");
                final int[] arr = new int[parts.length];
                for (int i = 0; i < arr.length; i++) {
                    arr[i] = Integer.valueOf(parts[i].trim());
                }
                defaultArray = arr;
            }
        } catch (final Exception e) {
            LOGGER.warn(e.getMessage());
        }

        int[] array = null;
        if (storeTrimSizes != null && storeTrimSizes.length > 0) {
            if (defaultArray != null && defaultArray.length > storeTrimSizes.length) {
                // There are more entries in the global settings so keep
                // them and copy over user settings that exist.
                array = defaultArray;
                System.arraycopy(storeTrimSizes, 0, array, 0, storeTrimSizes.length);
            } else {
                // User settings have more entries than global settings so
                // keep the user settings.
                array = storeTrimSizes;
            }
        } else {
            array = defaultArray;
        }

        return array;
    }

    public void addQueue(final UnsafePairQueue<String, Item> newQueue, final HasTerminate hasTerminate) {
        if (newQueue != null) {
            if (hasTerminate.isTerminated()) {
                // Clear the queue if we should terminate.
                pendingMerges.clear();

            } else {
                // Add the new queue to the pending merge queue ready for
                // merging.
                try {
                    pendingMerges.put(newQueue);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOGGER.warn("Thread interrupted while trying to put an item onto pendingMerges queue");
                } catch (final RuntimeException e) {
                    LOGGER.error(e.getMessage(), e);
                }

                if (!Thread.currentThread().isInterrupted()) {
                    // Try and merge all of the items on the pending merge queue.
                    mergePending(hasTerminate);
                }
            }
        }
    }

    private void mergePending(final HasTerminate hasTerminate) {
        // Only 1 thread will get to do a merge.
        if (merging.compareAndSet(false, true)) {
            try {
                if (hasTerminate.isTerminated()) {
                    // Clear the queue if we should terminate.
                    pendingMerges.clear();

                } else {
                    UnsafePairQueue<String, Item> queue = pendingMerges.poll();
                    while (queue != null) {
                        try {
                            mergeQueue(queue);
                        } catch (final RuntimeException e) {
                            LOGGER.error(e.getMessage(), e);
                            throw e;
                        }

                        if (hasTerminate.isTerminated()) {
                            // Clear the queue if we should terminate.
                            pendingMerges.clear();
                        }

                        queue = pendingMerges.poll();
                    }
                }
            } finally {
                merging.set(false);
            }

            if (hasTerminate.isTerminated()) {
                // Clear the queue if we should terminate.
                pendingMerges.clear();
            }

            // Make sure we don't fail to merge items from the queue that have
            // just been added by another thread that didn't get to do the
            // merge.
            if (pendingMerges.peek() != null) {
                mergePending(hasTerminate);
            }
        }
    }

    private void mergeQueue(final UnsafePairQueue<String, Item> newQueue) {
        /*
         * Update the total number of results that we have received.
         */
        totalResults.getAndAdd(newQueue.size());

        if (currentQueue == null) {
            currentQueue = updateResultStore(newQueue);

        } else {
            final PairQueue<String, Item> outputQueue = new UnsafePairQueue<>();

            /*
             * Create a partitioner to perform result reduction if needed.
             */
            final ItemPartitioner partitioner = new ItemPartitioner(compiledDepths.getDepths(),
                    compiledDepths.getMaxDepth());
            partitioner.setOutputCollector(outputQueue);

            /* First deal with the current queue. */
            partitioner.read(currentQueue);

            /* New deal with the new queue. */
            partitioner.read(newQueue);

            /* Perform partitioning and reduction. */
            partitioner.partition();

            currentQueue = updateResultStore(outputQueue);
        }
    }

    private PairQueue<String, Item> updateResultStore(final PairQueue<String, Item> queue) {
        // Stick the new reduced results into a new result store.
        final ResultStoreCreator resultStoreCreator = new ResultStoreCreator(compiledSorter);
        resultStoreCreator.read(queue);

        // Trim the number of results in the store.
        resultStoreCreator.trim(storeTrimSizes);

        // Put the remaining items into the current queue ready for the next
        // result.
        final PairQueue<String, Item> remaining = new UnsafePairQueue<>();
        long size = 0;
        for (final Items<Item> items : resultStoreCreator.childMap.values()) {
            for (final Item item : items) {
                remaining.collect(item.groupKey, item);
                size++;
            }
        }

        // Update the result store reference to point at this new store.
        this.resultStore = resultStoreCreator.create(size, totalResults.get());

        // Give back the remaining queue items ready for the next result.
        return remaining;
    }

    @Override
    public boolean shouldTerminateSearch() {
        if (!compiledSorter.hasSort() && !compiledDepths.hasGroupBy()) {
            if (resultStore != null && resultStore.getTotalSize() >= storeTrimSizes[0]) {
                return true;
            }
        }
        return false;
    }

    public ResultStore getResultStore() {
        return resultStore;
    }

    public boolean busy() {
        return pendingMerges.size() > 0 || merging.get();
    }
}
