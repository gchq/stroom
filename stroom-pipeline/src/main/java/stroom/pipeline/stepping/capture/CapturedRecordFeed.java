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

import stroom.pipeline.shared.stepping.StepLocation;

import java.util.HashSet;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Hands a consumer each record its producer has captured, <b>as they appear</b>, blocking in between.
 * <p>
 * This is the join between two stages. Today's reprocess reads a fixed {@code [first, last]} out of the store
 * because its upstream has always finished before it starts; a stage cannot assume that - its parent is still
 * producing - so it follows the parent's {@link CaptureWatermark} instead: consume what is available, wait
 * when caught up, and stop when the parent has stopped and everything it produced has been consumed.
 * <p>
 * Three rules earn their keep here, all of them about not hanging rather than about being wrong:
 * <ul>
 *   <li>The progress version is read <b>before</b> the available range, so a record landing between the two
 *   cannot be missed by the following wait (the lost-wake-up guard).</li>
 *   <li>Waits are bounded slices rather than one long block, so termination is noticed promptly even though
 *   nothing signals the watermark when a consumer is cancelled.</li>
 *   <li>A parent that <em>failed</em> still ends the feed. {@link CaptureWatermark#markError} marks the
 *   producer stopped precisely so consumers stop waiting; the failure is reported through the outcome rather
 *   than by leaving the consumer blocked.</li>
 * </ul>
 * <p>
 * Record indices are taken from the watermark, never assumed to start at zero: reader and text elements are
 * 1-based where SAX elements are 0-based.
 * <p>
 * <b>No callers yet, deliberately.</b> This was built for a decomposition into concurrent per-element stages,
 * which {@code stepping-design.md} §11 records as considered and set aside - the prize turned out to be
 * pipeline parallelism rather than less work. It is kept because the direction that replaced it, materialising
 * an edited element on demand per record, needs the same answer: a filtered navigation off the current step has to materialise records
 * in the direction of travel until one matches, consuming them as they appear exactly like this.
 */
public class CapturedRecordFeed {

    private static final long DEFAULT_POLL_MS = 250;

    /**
     * How the feed ended.
     */
    public enum Outcome {
        /** The producer finished cleanly and everything it captured was consumed. */
        COMPLETED,
        /** The producer failed. Whatever it did capture before failing has been consumed. */
        PARENT_FAILED,
        /** The consumer was terminated; the feed stopped early. */
        TERMINATED
    }

    /**
     * Receives the records, framed by part. {@link #startPart} and {@link #endPart} bracket each part's
     * records so a consumer can drive element lifecycle around them, and are always balanced - a part that has
     * been started is always ended, including when the feed stops early.
     */
    public interface RecordConsumer {

        void startPart(long partIndex);

        void record(StepLocation location);

        void endPart(long partIndex);
    }

    private final CaptureWatermark parent;
    private final long metaId;
    private final BooleanSupplier terminated;
    private final long pollMs;

    public CapturedRecordFeed(final CaptureWatermark parent,
                              final long metaId,
                              final BooleanSupplier terminated) {
        this(parent, metaId, terminated, DEFAULT_POLL_MS);
    }

    public CapturedRecordFeed(final CaptureWatermark parent,
                              final long metaId,
                              final BooleanSupplier terminated,
                              final long pollMs) {
        this.parent = parent;
        this.metaId = metaId;
        this.terminated = terminated;
        this.pollMs = pollMs;
    }

    /**
     * Feed every record the parent captures to {@code consumer}, blocking until the parent stops.
     *
     * @return how the feed ended. A {@link Outcome#PARENT_FAILED} still means everything the parent managed to
     * capture was delivered first.
     */
    public Outcome consume(final RecordConsumer consumer) {
        final Set<Long> finishedParts = new HashSet<>();
        Long openPart = null;
        long nextRecordIndex = -1;

        while (true) {
            if (terminated.getAsBoolean()) {
                return endEarly(consumer, openPart);
            }

            // Sampled BEFORE anything below reads the captured range, so that a record arriving between the
            // read and the wait at the bottom of this loop still counts as progress and does not sleep.
            final long version = parent.getVersion();
            final boolean parentStopped = parent.hasEnded();
            final List<Long> parts = parent.getCapturedPartIndices();

            if (openPart == null) {
                final OptionalLong nextPart = nextUnfinishedPart(parts, finishedParts);
                if (nextPart.isEmpty()) {
                    if (parentStopped) {
                        // Nothing left and nothing more coming. An empty producer lands here too, having
                        // never opened a part.
                        return parent.isSuccessfullyCaptured() ? Outcome.COMPLETED : Outcome.PARENT_FAILED;
                    }
                    parent.awaitChangeSince(version, pollMs);
                    continue;
                }
                openPart = nextPart.getAsLong();
                // Where this part actually starts, which is not necessarily record 0.
                nextRecordIndex = parent.getCapturedFirstRecordIndex(openPart);
                consumer.startPart(openPart);
            }

            final long lastAvailable = parent.getCapturedLastRecordIndex(openPart);
            boolean consumedAny = false;
            while (nextRecordIndex <= lastAvailable) {
                if (terminated.getAsBoolean()) {
                    return endEarly(consumer, openPart);
                }
                consumer.record(new StepLocation(metaId, openPart, nextRecordIndex));
                nextRecordIndex++;
                consumedAny = true;
            }
            if (consumedAny) {
                // Re-sample rather than wait: the parent may well have moved on while we were consuming.
                continue;
            }

            // Caught up with this part. It is final once the parent has stopped, or once a higher part has
            // appeared - a producer works parts in ascending order, so a later part means this one is done.
            if (parentStopped || hasHigherPart(parts, openPart)) {
                consumer.endPart(openPart);
                finishedParts.add(openPart);
                openPart = null;
                continue;
            }

            parent.awaitChangeSince(version, pollMs);
        }
    }

    private Outcome endEarly(final RecordConsumer consumer, final Long openPart) {
        if (openPart != null) {
            // Keep start/end balanced so the consumer can close down whatever it opened.
            consumer.endPart(openPart);
        }
        return Outcome.TERMINATED;
    }

    private static OptionalLong nextUnfinishedPart(final List<Long> parts, final Set<Long> finishedParts) {
        return parts.stream()
                .filter(part -> !finishedParts.contains(part))
                .mapToLong(Long::longValue)
                .min();
    }

    private static boolean hasHigherPart(final List<Long> parts, final long partIndex) {
        return parts.stream().anyMatch(part -> part > partIndex);
    }
}
