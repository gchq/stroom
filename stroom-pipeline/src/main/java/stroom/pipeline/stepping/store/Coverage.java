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

package stroom.pipeline.stepping.store;

import java.util.List;

/**
 * What a producer has captured: per part, <b>which records are held, and is the extent final</b>.
 * <p>
 * This is the one question the whole read side asks, and before this type existed it was answered by four
 * overlapping vocabularies - watermark first/last, {@code CapturedRange}, the segment files' extent maps and
 * {@code getElementRecordBound} - each grown where a bug surfaced. Containment ("can this record be served"),
 * bounds ("where does the stream reach"), the scan frontier ("where did the last window end") and completeness
 * ("is there more to come") are all queries on coverage; {@code CapturedRange} is its navigation view, not a
 * sibling abstraction. See {@code stepping-design.md} §11, <i>Target</i>.
 * <p>
 * Answers are <b>live</b>, not snapshots: a coverage is handed out once and queried while its producer runs,
 * so implementations must read through to current state. A cached answer would either hide records that have
 * arrived or - worse - admit ones that have not.
 * <p>
 * The three implementations mirror the three producers: a store-wide record coverage
 * ({@link StepDataStore#recordCoverage}), a per-{@code (element, fingerprint)} coverage
 * ({@link StepDataStore#elementCoverage}) whose holdings may be sparse, and a sweep's watermark coverage
 * ({@code StreamSweep.coverage()}) which tracks what the producer has <i>signalled</i> rather than what is on
 * disk - the distinction that keeps a reader from seeing a record before its producer says it is whole.
 */
public interface Coverage {

    /**
     * The value meaning "no record": returned by {@link #first}/{@link #last} for a part with nothing held.
     */
    long NONE = -1;

    /**
     * @return the parts that hold anything, ascending. Navigation enumerates parts before it can ask about
     * records in them, so a coverage that could not answer this would leave its callers reaching back into
     * the store for half the question.
     */
    List<Long> parts();

    /**
     * @return the lowest record index held for the part, or {@link #NONE}.
     */
    long first(long partIndex);

    /**
     * @return the highest record index held for the part, or {@link #NONE}.
     */
    long last(long partIndex);

    /**
     * @return true if the record is actually held - not merely within the span. The two differ once records
     * are materialised individually: a sparse element has gaps inside its own bounds, and serving a gap shows
     * the user a blank pane.
     */
    boolean holds(long partIndex, long recordIndex);

    /**
     * @return true if no further records will ever be added: {@link #last} is the true end, so an absent
     * record means "does not exist" rather than "not captured yet". This is the bit that turns a reader's
     * wait into an answer - false means anything outside the held range may still arrive.
     */
    boolean isExtentFinal();
}
