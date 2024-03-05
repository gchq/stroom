package stroom.job.client.view;

public class DateRecord {

    private final int year;
    private final int month;
    private final int day;

    public DateRecord(final int year, final int month, final int day) {
        this.year = year;
        this.month = month;
        this.day = day;
    }

    public int getYear() {
        return year;
    }

    public int getMonth() {
        return month;
    }

    public int getDay() {
        return day;
    }
}
