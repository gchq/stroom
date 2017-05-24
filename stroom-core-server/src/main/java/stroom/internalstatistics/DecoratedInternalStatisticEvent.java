package stroom.internalstatistics;

import com.google.common.base.Preconditions;
import stroom.query.api.v1.DocRef;

import java.util.Map;

public class DecoratedInternalStatisticEvent {

    private final InternalStatisticEvent internalStatisticEvent;
    private final DocRef docRef;

    public DecoratedInternalStatisticEvent(final InternalStatisticEvent internalStatisticEvent,
                                           final DocRef docRef) {

        this.internalStatisticEvent = Preconditions.checkNotNull(internalStatisticEvent);
        this.docRef = Preconditions.checkNotNull(docRef);
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public static InternalStatisticEvent createPlusOneCountStat(final String key, final long timeMs, final Map<String, String> tags) {
        return InternalStatisticEvent.createPlusOneCountStat(key, timeMs, tags);
    }

    public static InternalStatisticEvent createPlusNCountStat(final String key, final long timeMs, final Map<String, String> tags, final long count) {
        return InternalStatisticEvent.createPlusNCountStat(key, timeMs, tags, count);
    }

    public static InternalStatisticEvent createValueStat(final String key, final long timeMs, final Map<String, String> tags, final double value) {
        return InternalStatisticEvent.createValueStat(key, timeMs, tags, value);
    }

    public String getKey() {
        return internalStatisticEvent.getKey();
    }

    public InternalStatisticEvent.Type getType() {
        return internalStatisticEvent.getType();
    }

    public long getTimeMs() {
        return internalStatisticEvent.getTimeMs();
    }

    public Map<String, String> getTags() {
        return internalStatisticEvent.getTags();
    }

    public Object getValue() {
        return internalStatisticEvent.getValue();
    }

    public Long getValueAsLong() {
        return internalStatisticEvent.getValueAsLong();
    }

    public Double getValueAsDouble() {
        return internalStatisticEvent.getValueAsDouble();
    }

    @Override
    public String toString() {
        return "DecoratedInternalStatisticEvent{" +
                "internalStatisticEvent=" + internalStatisticEvent +
                ", docRef=" + docRef +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final DecoratedInternalStatisticEvent that = (DecoratedInternalStatisticEvent) o;

        if (!internalStatisticEvent.equals(that.internalStatisticEvent)) return false;
        return docRef.equals(that.docRef);
    }

    @Override
    public int hashCode() {
        int result = internalStatisticEvent.hashCode();
        result = 31 * result + docRef.hashCode();
        return result;
    }
}
