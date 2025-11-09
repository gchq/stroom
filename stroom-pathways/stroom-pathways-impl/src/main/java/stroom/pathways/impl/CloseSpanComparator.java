package stroom.pathways.impl;

import stroom.pathways.shared.otel.trace.NanoDuration;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.otel.trace.Span;
import stroom.util.shared.time.SimpleDuration;

import java.util.Comparator;

public class CloseSpanComparator implements Comparator<Span> {

    private final NanoDuration tolerance;

    public CloseSpanComparator(final SimpleDuration simpleDuration) {
        if (simpleDuration == null) {
            tolerance = NanoDuration.ZERO;
        } else {
            this.tolerance = switch (simpleDuration.getTimeUnit()) {
                case NANOSECONDS -> NanoDuration.ofNanos(simpleDuration.getTime());
                case MILLISECONDS -> NanoDuration.ofMillis(simpleDuration.getTime());
                case SECONDS -> NanoDuration.ofSeconds(simpleDuration.getTime());
                case MINUTES -> NanoDuration.ofSeconds(simpleDuration.getTime() * 60);
                case HOURS -> NanoDuration.ofSeconds(simpleDuration.getTime() * 60 * 60);
                default -> throw new RuntimeException("Unable to convert simple duration");
            };
        }
    }

    public CloseSpanComparator(final NanoDuration tolerance) {
        this.tolerance = tolerance;
    }

    @Override
    public int compare(final Span o1, final Span o2) {
        final NanoTime start1 = NanoTime.fromString(o1.getStartTimeUnixNano());
        final NanoTime start2 = NanoTime.fromString(o2.getStartTimeUnixNano());
        final NanoDuration diff = start1.diff(start2);
        // If there is less duration than the supplied tolerance between then sort by name.
        if (diff.isLessThanEquals(tolerance)) {
            return o1.getName().compareTo(o2.getName());
        }
        return start1.compareTo(start2);
    }
}
