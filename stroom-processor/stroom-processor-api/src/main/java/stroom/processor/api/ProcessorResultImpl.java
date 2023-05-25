package stroom.processor.api;

import stroom.util.NullSafe;
import stroom.util.shared.Severity;

import java.util.Map;

public class ProcessorResultImpl implements ProcessorResult {

    private final long read;
    private final long written;
    private final Map<Severity, Long> markerCounts;

    public ProcessorResultImpl(final long read,
                               final long written,
                               final Map<Severity, Long> markerCounts) {
        this.read = read;
        this.written = written;
        this.markerCounts = markerCounts;
    }

    @Override
    public long getRead() {
        return read;
    }

    @Override
    public long getWritten() {
        return written;
    }

    @Override
    public long getMarkerCount(final Severity... severity) {
        return NullSafe.stream(severity)
                .mapToLong(sev ->
                        markerCounts.getOrDefault(sev, 0L))
                .sum();
    }

    @Override
    public String toString() {
        return "ProcessorResultImpl{" +
                "read=" + read +
                ", written=" + written +
                ", markerCounts=" + markerCounts +
                '}';
    }
}
