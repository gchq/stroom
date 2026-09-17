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

package stroom.pipeline.stepping.capture;

/**
 * An element that carries a running count across the records of a stream, and can therefore be restarted
 * mid-stream if that count is given back to it.
 * <p>
 * This exists because on-demand replay re-runs an element for a <em>single</em> record without processing the
 * records before it. Anything derived purely from the element's input survives that untouched; a counter does
 * not, because its value is a function of every record the element has already seen. A fresh instance would
 * start from zero and, for the {@code EventId} that {@link stroom.pipeline.filter.IdEnrichmentFilter} writes,
 * report record 500 as event 1.
 * <p>
 * The count is captured after each record during a sweep (see {@code SteppingController.captureRecord}) and
 * handed back before a replayed record (see {@code ReprocessDriver}). Implementations should treat
 * {@link #setSteppingCount} as "you have already counted this many", i.e. the next thing counted is
 * {@code count + 1}.
 * <p>
 * Only elements whose count is <em>observable in their output</em> need implement this. A counter used purely
 * for processing statistics does not change what a user sees while stepping, and capturing it would be cost
 * for nothing.
 */
public interface SteppingCounter {

    /**
     * @return the number of items this element has counted so far in this stream.
     */
    long getSteppingCount();

    /**
     * Resume from a previously captured count.
     *
     * @param count the number of items already counted, so the next is {@code count + 1}.
     */
    void setSteppingCount(long count);
}
