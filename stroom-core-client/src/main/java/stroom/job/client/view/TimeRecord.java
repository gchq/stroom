package stroom.job.client.view;

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
}
