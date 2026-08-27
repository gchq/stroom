/*
 * Copyright 2026 Crown Copyright
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

package stroom.processor.impl;

import stroom.processor.shared.ProcessorFilter;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * Remembers which filters had no created tasks to queue, so that we don't keep asking the database
 * the same question.
 * <p>
 * The queue is filled after every assignment and whenever a node is told there is nothing to do, so
 * fills run more or less back to back. Each filter considered by a fill costs a query, and a fill
 * only stops early once every processing profile has enough tasks queued, which a profile with no
 * work never does. Without this, every fill would query for every filter of any profile that has
 * nothing to do.
 * </p>
 * <p>
 * This is state held in memory only, and only the master node fills the queue. The tasks it is
 * about are queued in memory by that node, so there is nothing to persist; if master ship moves,
 * the new master simply asks for everything once.
 * </p>
 */
@Singleton
public class FilterFetchBackoff {

    private final Map<Integer, FilterState> stateByFilterId = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    @Inject
    public FilterFetchBackoff() {
        this(System::currentTimeMillis);
    }

    FilterFetchBackoff(final LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * @return True if we should look for created tasks for this filter now.
     */
    public boolean isFetchDue(final ProcessorFilter filter,
                              final StroomDuration skipEmptyFilterFetchDuration) {
        if (isTurnedOff(skipEmptyFilterFetchDuration)) {
            return true;
        }
        final FilterState state = stateByFilterId.get(filter.getId());
        return state == null || state.isFetchDue(clock.getAsLong());
    }

    /**
     * Read before looking for tasks so that the result can be passed to
     * {@link #recordEmptyFetch(ProcessorFilter, StroomDuration, long)}, which uses it to tell
     * whether task creation has produced anything for this filter while we were looking.
     */
    public long getCreationVersion(final ProcessorFilter filter) {
        final FilterState state = stateByFilterId.get(filter.getId());
        return state == null
                ? 0
                : state.getCreationVersion();
    }

    /**
     * Record that we looked for created tasks for this filter and found none, so that we leave it
     * alone for a while.
     *
     * @param creationVersionBeforeFetch The value {@link #getCreationVersion(ProcessorFilter)}
     *                                   returned before we looked. If task creation has created
     *                                   tasks for this filter since then, what we found is already
     *                                   out of date and the filter must not be backed off.
     */
    public void recordEmptyFetch(final ProcessorFilter filter,
                                 final StroomDuration skipEmptyFilterFetchDuration,
                                 final long creationVersionBeforeFetch) {
        if (isTurnedOff(skipEmptyFilterFetchDuration)) {
            return;
        }
        // Timed from now rather than from the start of the fill. A fill that considers a lot of
        // filters can take longer than the wait itself, which would leave every filter it backed
        // off due again immediately and defeat the whole point.
        final long nextFetchMs = clock.getAsLong() + skipEmptyFilterFetchDuration.toMillis();
        getState(filter).recordEmptyFetch(nextFetchMs, creationVersionBeforeFetch);
    }

    /**
     * Record that this filter had created tasks for us, so it is worth asking again straight away.
     */
    public void recordFetchedTasks(final ProcessorFilter filter) {
        getState(filter).recordFetchDue();
    }

    /**
     * Record that task creation has just created tasks for this filter, so there is something to
     * queue however recently we looked and found nothing. Must only be called once the tasks have
     * been committed, otherwise a concurrent fill can find nothing and still back the filter off.
     */
    public void recordTasksCreated(final ProcessorFilter filter) {
        getState(filter).recordTasksCreated();
    }

    /**
     * Forget any filters that are no longer being considered, e.g. because they have been disabled
     * or deleted, so this doesn't grow without limit.
     */
    public void retainAll(final Collection<ProcessorFilter> filters) {
        // Only worth walking the filters if we are remembering ones that are no longer in the list.
        if (stateByFilterId.size() > filters.size()) {
            final Set<Integer> filterIds = filters
                    .stream()
                    .map(ProcessorFilter::getId)
                    .collect(Collectors.toSet());
            stateByFilterId.keySet().retainAll(filterIds);
        }
    }

    public int size() {
        return stateByFilterId.size();
    }

    private FilterState getState(final ProcessorFilter filter) {
        return stateByFilterId.computeIfAbsent(filter.getId(), id -> new FilterState());
    }

    private boolean isTurnedOff(final StroomDuration skipEmptyFilterFetchDuration) {
        return skipEmptyFilterFetchDuration == null || skipEmptyFilterFetchDuration.isZero();
    }


    // --------------------------------------------------------------------------------


    /**
     * What we know about one filter. Task creation and the queue fill run on different threads, so
     * the fetch time and the creation version have to be read and written together to decide
     * whether what a fill found is still current.
     */
    private static final class FilterState {

        private long nextFetchMs;
        private long creationVersion;

        private synchronized boolean isFetchDue(final long nowMs) {
            return nowMs >= nextFetchMs;
        }

        private synchronized long getCreationVersion() {
            return creationVersion;
        }

        private synchronized void recordEmptyFetch(final long nextFetchMs,
                                                   final long creationVersionBeforeFetch) {
            if (creationVersion == creationVersionBeforeFetch) {
                this.nextFetchMs = nextFetchMs;
            }
            // Otherwise tasks were created for this filter while we were looking, so what we found
            // tells us nothing and the filter stays due a fetch.
        }

        private synchronized void recordFetchDue() {
            nextFetchMs = 0;
        }

        private synchronized void recordTasksCreated() {
            creationVersion++;
            nextFetchMs = 0;
        }
    }
}
