package stroom.internalstatistics;

import com.google.common.base.Preconditions;

import java.util.Map;

public class InternalStatisticEvent {
    private final String key;
    private final Type type;
    private final long timeMs;
    private final Map<String, String> tags;
    private final Object value;

    public enum Type {
        COUNT,
        VALUE
    }

    public static InternalStatisticEvent createPlusOneCountStat(final String key, final long timeMs, final Map<String, String> tags){
        return new InternalStatisticEvent(key, Type.COUNT, timeMs, tags, 1L);
    }

    public static InternalStatisticEvent createPlusNCountStat(final String key, final long timeMs, final Map<String, String> tags, final long count){
        return new InternalStatisticEvent(key, Type.COUNT, timeMs, tags, count);
    }

    public static InternalStatisticEvent createValueStat(final String key, final long timeMs, final Map<String, String> tags, final double value){
        return new InternalStatisticEvent(key, Type.VALUE, timeMs, tags, value);
    }

    private InternalStatisticEvent(final String key, final Type type, final long timeMs, final Map<String, String> tags, final Object value) {
        Preconditions.checkArgument(timeMs >= 0);
        this.key = Preconditions.checkNotNull(key);
        this.type = Preconditions.checkNotNull(type);
        this.timeMs = timeMs;
        this.tags = Preconditions.checkNotNull(tags);
        this.value = Preconditions.checkNotNull(value);
    }

    public String getKey() {
        return key;
    }

    public Type getType() {
        return type;
    }

    public long getTimeMs() {
        return timeMs;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public Object getValue() {
        return value;
    }

    public Long getValueAsLong() {
        if (type.equals(Type.VALUE)) {
            throw  new UnsupportedOperationException("getValueAsDouble is not supported for a VALUE statistic");
        }
        return (Long) value;
    }

    public Double getValueAsDouble() {
        if (type.equals(Type.COUNT)) {
            throw  new UnsupportedOperationException("getValueAsDouble is not supported for a COUNT statistic");
        }
        return (Double) value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final InternalStatisticEvent that = (InternalStatisticEvent) o;

        if (timeMs != that.timeMs) return false;
        if (!key.equals(that.key)) return false;
        if (!tags.equals(that.tags)) return false;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        int result = key.hashCode();
        result = 31 * result + (int) (timeMs ^ (timeMs >>> 32));
        result = 31 * result + tags.hashCode();
        result = 31 * result + value.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "InternalStatisticEvent{" +
                "key='" + key + '\'' +
                ", timeMs=" + timeMs +
                ", tags=" + tags +
                ", value=" + value +
                '}';
    }
}
