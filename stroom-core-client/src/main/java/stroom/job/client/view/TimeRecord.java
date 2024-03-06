package stroom.job.client.view;

import java.util.Objects;

public class TimeRecord {

    private final int hour;
    private final int minute;
    private final int second;
    private final int millisecond;

    public TimeRecord(final int hour,
                      final int minute,
                      final int second,
                      final int millisecond) {
        this.hour = hour;
        this.minute = minute;
        this.second = second;
        this.millisecond = millisecond;
    }

    public int getHour() {
        return hour;
    }

    public int getMinute() {
        return minute;
    }

    public int getSecond() {
        return second;
    }

    public int getMillisecond() {
        return millisecond;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TimeRecord that = (TimeRecord) o;
        return hour == that.hour && minute == that.minute && second == that.second && millisecond == that.millisecond;
    }

    @Override
    public int hashCode() {
        return Objects.hash(hour, minute, second, millisecond);
    }

    @Override
    public String toString() {
        return "TimeRecord{" +
                "hour=" + hour +
                ", minute=" + minute +
                ", second=" + second +
                ", millisecond=" + millisecond +
                '}';
    }
}
