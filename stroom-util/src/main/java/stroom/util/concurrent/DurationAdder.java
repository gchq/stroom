package stroom.util.concurrent;

import stroom.util.logging.DurationTimer;

import java.time.Duration;
import java.time.temporal.TemporalUnit;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link Duration} that can be added to in a thread safe, non-blocking, way.
 */
public class DurationAdder implements Comparable<DurationAdder> {

    private final AtomicReference<Duration> durationRef;

    public DurationAdder() {
        this.durationRef = new AtomicReference<>(Duration.ZERO);
    }

    public DurationAdder(final Duration duration) {
        Objects.requireNonNull(duration);
        this.durationRef = new AtomicReference<>(duration);
    }

    /**
     * Add duration to the current duration.
     *
     * @return The duration after adding duration
     */
    public Duration add(final Duration duration) {
        Objects.requireNonNull(duration);
        return durationRef.accumulateAndGet(
                duration,
                Duration::plus);
    }

    /**
     * Add durationAdder's duration to the current duration.
     *
     * @return The duration after adding durationAdder
     */
    public Duration add(final DurationAdder durationAdder) {
        Objects.requireNonNull(durationAdder);
        return durationRef.accumulateAndGet(
                durationAdder.get(),
                Duration::plus);
    }

    /**
     * Add durationTimer's duration to the current duration.
     *
     * @return The duration after adding durationTimer
     */
    public Duration add(final DurationTimer durationTimer) {
        Objects.requireNonNull(durationTimer);
        return durationRef.accumulateAndGet(
                durationTimer.get(),
                Duration::plus);
    }

    /**
     * @return The current duration
     */
    public Duration get() {
        return durationRef.get();
    }

    public long get(final TemporalUnit unit) {
        return durationRef.get().get(unit);
    }

    public List<TemporalUnit> getUnits() {
        return durationRef.get().getUnits();
    }

    public boolean isZero() {
        return durationRef.get().isZero();
    }

    public boolean isNegative() {
        return durationRef.get().isNegative();
    }

    public long getSeconds() {
        return durationRef.get().getSeconds();
    }

    public int getNano() {
        return durationRef.get().getNano();
    }

    public Duration withSeconds(final long seconds) {
        return durationRef.get().withSeconds(seconds);
    }

    public Duration withNanos(final int nanoOfSecond) {
        return durationRef.get().withNanos(nanoOfSecond);
    }

    public long toDays() {
        return durationRef.get().toDays();
    }

    public long toHours() {
        return durationRef.get().toHours();
    }

    public long toMinutes() {
        return durationRef.get().toMinutes();
    }

    public long toSeconds() {
        return durationRef.get().toSeconds();
    }

    public long toMillis() {
        return durationRef.get().toMillis();
    }

    public long toNanos() {
        return durationRef.get().toNanos();
    }

    public long toDaysPart() {
        return durationRef.get().toDaysPart();
    }

    public int toHoursPart() {
        return durationRef.get().toHoursPart();
    }

    public int toMinutesPart() {
        return durationRef.get().toMinutesPart();
    }

    public int toSecondsPart() {
        return durationRef.get().toSecondsPart();
    }

    public int toMillisPart() {
        return durationRef.get().toMillisPart();
    }

    public int toNanosPart() {
        return durationRef.get().toNanosPart();
    }

    public Duration truncatedTo(final TemporalUnit unit) {
        return durationRef.get().truncatedTo(unit);
    }

    public int compareTo(final DurationAdder otherDuration) {
        return durationRef.get().compareTo(otherDuration.get());
    }

    @Override
    public String toString() {
        return durationRef.get().toString();
    }
}
