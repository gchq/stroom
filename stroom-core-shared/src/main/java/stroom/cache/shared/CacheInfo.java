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

package stroom.cache.shared;

import org.hibernate.stat.Statistics;

import stroom.pipeline.shared.data.PipelineElement;
import stroom.util.shared.SharedObject;

public class CacheInfo implements SharedObject {
    private static final long serialVersionUID = 463047159587522512L;

    private String cacheName;
    private int statisticsAccuracy;
    private long cacheHits;
    private long onDiskHits;
    private long offHeapHits;
    private long inMemoryHits;
    private long misses;
    private long onDiskMisses;
    private long offHeapMisses;
    private long inMemoryMisses;
    private long size;
    private long memoryStoreSize;
    private long offHeapStoreSize;
    private long diskStoreSize;
    private float averageGetTime;
    private long evictionCount;
    private long searchesPerSecond;
    private long averageSearchTime;
    private long writerQueueLength;

    public CacheInfo() {
        // Default constructor necessary for GWT serialisation.
    }

    public CacheInfo(final String cacheName, final long cacheHits, final long onDiskHits, final long offHeapHits,
            final long inMemoryHits, final long misses, final long onDiskMisses, final long offHeapMisses,
            final long inMemoryMisses, final long size, final float averageGetTime, final long evictionCount,
            final long memoryStoreSize, final long offHeapStoreSize, final long diskStoreSize,
            final long searchesPerSecond, final long averageSearchTime, final long writerQueueLength) {
        this.cacheName = cacheName;
        this.cacheHits = cacheHits;
        this.onDiskHits = onDiskHits;
        this.offHeapHits = offHeapHits;
        this.inMemoryHits = inMemoryHits;
        this.misses = misses;
        this.onDiskMisses = onDiskMisses;
        this.offHeapMisses = offHeapMisses;
        this.inMemoryMisses = inMemoryMisses;
        this.size = size;
        this.averageGetTime = averageGetTime;
        this.evictionCount = evictionCount;
        this.memoryStoreSize = memoryStoreSize;
        this.offHeapStoreSize = offHeapStoreSize;
        this.diskStoreSize = diskStoreSize;
        this.searchesPerSecond = searchesPerSecond;
        this.averageSearchTime = averageSearchTime;
        this.writerQueueLength = writerQueueLength;
    }

    /**
     * The number of times a requested item was found in the cache.
     */
    public long getCacheHits() {
        return cacheHits;
    }

    /**
     * Number of times a requested item was found in the Memory Store.
     */
    public long getInMemoryHits() {
        return inMemoryHits;
    }

    /**
     * Number of times a requested item was found in the off-heap store.
     */
    public long getOffHeapHits() {
        return offHeapHits;
    }

    /**
     * Number of times a requested item was found in the Disk Store.
     *
     * @return the number of times a requested item was found on Disk, or 0 if
     *         there is no disk storage configured.
     */
    public long getOnDiskHits() {
        return onDiskHits;
    }

    /**
     * @return the number of times a requested element was not found in the
     *         cache
     */
    public long getCacheMisses() {
        return misses;

    }

    /**
     * Number of times a requested item was not found in the Memory Store.
     */
    public long getInMemoryMisses() {
        return inMemoryMisses;
    }

    /**
     * Number of times a requested item was not found in the off-heap store.
     */
    public long getOffHeapMisses() {
        return offHeapMisses;
    }

    /**
     * Number of times a requested item was not found in the Disk Store.
     *
     * @return the number of times a requested item was not found on Disk, or 0
     *         if there is no disk storage configured.
     */
    public long getOnDiskMisses() {
        return onDiskMisses;
    }

    /**
     * Gets the number of elements stored in the cache. Calculating this can be
     * expensive. Accordingly, this method will return three different values,
     * depending on the statistics accuracy setting.
     * <h3>Best Effort Size</h3> This result is returned when the statistics
     * accuracy setting is {@link Statistics#STATISTICS_ACCURACY_BEST_EFFORT}.
     * <p/>
     * The size is the number of {@link PipelineElement}s in the
     * {@link net.sf.ehcache.store.MemoryStore} plus the number of
     * {@link PipelineElement}s in the
     * {@link net.sf.ehcache.store.disk.DiskStore}.
     * <p/>
     * This number is the actual number of elements, including expired elements
     * that have not been removed. Any duplicates between stores are accounted
     * for.
     * <p/>
     * Expired elements are removed from the the memory store when getting an
     * expired element, or when attempting to spool an expired element to disk.
     * <p/>
     * Expired elements are removed from the disk store when getting an expired
     * element, or when the expiry thread runs, which is once every five
     * minutes.
     * <p/>
     * <h3>Guaranteed Accuracy Size</h3> This result is returned when the
     * statistics accuracy setting is
     * {@link Statistics#STATISTICS_ACCURACY_GUARANTEED}.
     * <p/>
     * This method accounts for elements which might be expired or duplicated
     * between stores. It take approximately 200ms per 1000 elements to execute.
     * <h3>Fast but non-accurate Size</h3> This result is returned when the
     * statistics accuracy setting is {@link #STATISTICS_ACCURACY_NONE}.
     * <p/>
     * The number given may contain expired elements. In addition if the
     * DiskStore is used it may contain some double counting of elements. It
     * takes 6ms for 1000 elements to execute. Time to execute is O(log n).
     * 50,000 elements take 36ms.
     *
     * @return the number of elements in the ehcache, with a varying degree of
     *         accuracy, depending on accuracy setting.
     */
    public long getObjectCount() {
        return size;
    }

    /**
     * @return the number of objects in the memory store
     */
    public long getMemoryStoreObjectCount() {
        return memoryStoreSize;
    }

    /**
     * @return the number of objects in the off-heap store
     */
    public long getOffHeapStoreObjectCount() {
        return offHeapStoreSize;
    }

    /**
     * @return the number of objects in the disk store
     */
    public long getDiskStoreObjectCount() {
        return diskStoreSize;
    }

    /**
     * Accurately measuring statistics can be expensive. Returns the current
     * accuracy setting.
     *
     * @return one of {@link #STATISTICS_ACCURACY_BEST_EFFORT},
     *         {@link #STATISTICS_ACCURACY_GUARANTEED},
     *         {@link #STATISTICS_ACCURACY_NONE}
     */
    public int getStatisticsAccuracy() {
        return statisticsAccuracy;
    }

    /**
     * Accurately measuring statistics can be expensive. Returns the current
     * accuracy setting.
     *
     * @return a human readable description of the accuracy setting. One of
     *         "None", "Best Effort" or "Guaranteed".
     */
    public String getStatisticsAccuracyDescription() {
        if (statisticsAccuracy == 0) {
            return "None";
        } else if (statisticsAccuracy == 1) {
            return "Best Effort";
        } else {
            return "Guaranteed";
        }
    }

    /**
     * @return the name of the Ehcache, or null if a reference is no longer held
     *         to the cache, as, it would be after deserialization.
     */
    public String getName() {
        return cacheName;
    }

    /**
     * Returns a {@link String} representation of the {@link Ehcache}
     * statistics.
     */
    @Override
    public final String toString() {
        final StringBuilder dump = new StringBuilder();

        dump.append("[ ").append(" name = ").append(getName()).append(" cacheHits = ").append(cacheHits)
                .append(" onDiskHits = ").append(onDiskHits).append(" offHeapHits = ").append(offHeapHits)
                .append(" inMemoryHits = ").append(inMemoryHits).append(" misses = ").append(misses)
                .append(" onDiskMisses = ").append(onDiskMisses).append(" offHeapMisses = ").append(offHeapMisses)
                .append(" inMemoryMisses = ").append(inMemoryMisses).append(" size = ").append(size)
                .append(" averageGetTime = ").append(averageGetTime).append(" evictionCount = ").append(evictionCount)
                .append(" ]");

        return dump.toString();
    }

    /**
     * The average get time. Because ehcache support JDK1.4.2, each get time
     * uses System.currentTimeMilis, rather than nanoseconds. The accuracy is
     * thus limited.
     */
    public float getAverageGetTime() {
        return averageGetTime;
    }

    /**
     * Gets the number of cache evictions, since the cache was created, or
     * statistics were cleared.
     */
    public long getEvictionCount() {
        return evictionCount;
    }

    /**
     * Gets the average execution time (in milliseconds) within the last sample
     * period.
     */
    public long getAverageSearchTime() {
        return averageSearchTime;
    }

    /**
     * Get the number of search executions that have completed in the last
     * second.
     */
    public long getSearchesPerSecond() {
        return searchesPerSecond;
    }

    /**
     * Gets the size of the write-behind queue, if any. The value is for all
     * local buckets
     *
     * @return Elements waiting to be processed by the write behind writer. -1
     *         if no write-behind
     */
    public long getWriterQueueSize() {
        return writerQueueLength;
    }
}
