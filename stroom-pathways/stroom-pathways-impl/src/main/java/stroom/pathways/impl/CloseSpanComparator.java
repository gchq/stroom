package stroom.pathways.impl;

import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.otel.trace.Span;

import java.util.Comparator;

public class CloseSpanComparator implements Comparator<Span> {

    private final NanoTime tolerance;

    public CloseSpanComparator(final NanoTime tolerance) {
        this.tolerance = tolerance;
    }

    @Override
    public int compare(final Span o1, final Span o2) {
        final NanoTime start1 = NanoTime.fromString(o1.getStartTimeUnixNano());
        final NanoTime start2 = NanoTime.fromString(o2.getStartTimeUnixNano());
        final NanoTime diff = start1.diff(start2);
        // If there is less duration than the supplied tolerance between then sort by name.
        if (diff.isLessThanEquals(tolerance)) {
            return o1.getName().compareTo(o2.getName());
        }
        return start1.compareTo(start2);
    }
}
