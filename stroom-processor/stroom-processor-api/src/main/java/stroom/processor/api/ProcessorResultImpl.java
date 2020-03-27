package stroom.processor.api;

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
        long count = 0;
        for (final Severity sev : severity) {
            count += markerCounts.getOrDefault(sev, 0L);
        }
        return count;
    }
}
