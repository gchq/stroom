package stroom.internalstatistics;

import com.google.common.base.Preconditions;

import java.util.Map;

//TODO this was added with a view to abstracting internal statistics from th SQL stats implementation.
public class InternalStatisticEvent {
    private final String statisticName;
    private final Type type;
    private final long timeMs;
    private final Map<String, String> tags;
    private final Object value;

    public enum Type {
        COUNT,
        VALUE
    }

    public static InternalStatisticEvent createCount(final String statisticName, final long timeMs, final Map<String, String> tags){
        return new InternalStatisticEvent(statisticName, Type.COUNT, timeMs, tags, 1L);
    }

    public static InternalStatisticEvent createCount(final String statisticName, final long timeMs, final Map<String, String> tags, final long count){
        return new InternalStatisticEvent(statisticName, Type.COUNT, timeMs, tags, count);
    }

    public static InternalStatisticEvent createValue(final String statisticName, final long timeMs, final Map<String, String> tags, final double value){
        return new InternalStatisticEvent(statisticName, Type.VALUE, timeMs, tags, value);
    }

    private InternalStatisticEvent(final String statisticName, final Type type, final long timeMs, final Map<String, String> tags, final Object value) {
        Preconditions.checkArgument(timeMs >= 0);
        this.statisticName = Preconditions.checkNotNull(statisticName);
        this.type = Preconditions.checkNotNull(type);
        this.timeMs = timeMs;
        this.tags = Preconditions.checkNotNull(tags);
        this.value = Preconditions.checkNotNull(value);
    }

    public String getStatisticName() {
        return statisticName;
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

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final InternalStatisticEvent that = (InternalStatisticEvent) o;

        if (timeMs != that.timeMs) return false;
        if (!statisticName.equals(that.statisticName)) return false;
        if (!tags.equals(that.tags)) return false;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        int result = statisticName.hashCode();
        result = 31 * result + (int) (timeMs ^ (timeMs >>> 32));
        result = 31 * result + tags.hashCode();
        result = 31 * result + value.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "InternalStatisticEvent{" +
                "statisticName='" + statisticName + '\'' +
                ", timeMs=" + timeMs +
                ", tags=" + tags +
                ", value=" + value +
                '}';
    }
}
