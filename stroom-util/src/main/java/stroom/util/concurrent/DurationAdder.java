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

package stroom.util.concurrent;

import stroom.util.logging.DurationTimer;

import java.time.Duration;
import java.time.temporal.TemporalUnit;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;

/**
 * A {@link Duration} that can be added to in a thread safe, non-blocking, way.
 * Relies on {@link LongAdder} to accumulate the durations so is not designed for
 * any form of synchronisation control.
 * Does not implement equals.
 */
public class DurationAdder {

    // Duration consists of a seconds part and a nanos part so accumulate both
    // so that we can build a duration from it when we need it. LongAdder is stripped
    // so less contention than something like AtomicReference
    private final LongAdder secondsAdder = new LongAdder();
    private final LongAdder nanosAdder = new LongAdder();

    public DurationAdder() {
    }

    public DurationAdder(final Duration duration) {
        add(duration);
    }

    /**
     * Add duration to the current duration.
     *
     * @return The duration after adding duration
     */
    public void add(final Duration duration) {
        Objects.requireNonNull(duration);
        secondsAdder.add(duration.getSeconds());
        nanosAdder.add(duration.getNano());
    }

    /**
     * Add durationAdder's duration to the current duration.
     *
     * @return The duration after adding durationAdder
     */
    public void add(final DurationAdder durationAdder) {
        Objects.requireNonNull(durationAdder);
        this.secondsAdder.add(durationAdder.secondsAdder.sum());
        this.nanosAdder.add(durationAdder.nanosAdder.sum());
    }

    /**
     * Add durationTimer's duration to the current duration.
     *
     * @return The duration after adding durationTimer
     */
    public void add(final DurationTimer durationTimer) {
        Objects.requireNonNull(durationTimer);
        final Duration duration = durationTimer.get();
        add(duration);
    }

    /**
     * @return The current duration
     */
    public Duration get() {
        return Duration.ofSeconds(secondsAdder.sum(), nanosAdder.sum());
    }

    public long get(final TemporalUnit unit) {
        return get().get(unit);
    }

    public List<TemporalUnit> getUnits() {
        return get().getUnits();
    }

    public boolean isZero() {
        return get().isZero();
    }

    public boolean isNegative() {
        return get().isNegative();
    }

    public long getSeconds() {
        return get().getSeconds();
    }

    public int getNano() {
        return get().getNano();
    }

    public long toDays() {
        return get().toDays();
    }

    public long toHours() {
        return get().toHours();
    }

    public long toMinutes() {
        return get().toMinutes();
    }

    public long toSeconds() {
        return get().toSeconds();
    }

    public long toMillis() {
        return get().toMillis();
    }

    public long toNanos() {
        return get().toNanos();
    }

    public long toDaysPart() {
        return get().toDaysPart();
    }

    public int toHoursPart() {
        return get().toHoursPart();
    }

    public int toMinutesPart() {
        return get().toMinutesPart();
    }

    public int toSecondsPart() {
        return get().toSecondsPart();
    }

    public int toMillisPart() {
        return get().toMillisPart();
    }

    public int toNanosPart() {
        return get().toNanosPart();
    }

    public Duration truncatedTo(final TemporalUnit unit) {
        return get().truncatedTo(unit);
    }

    @Override
    public String toString() {
        return get().toString();
    }
}
