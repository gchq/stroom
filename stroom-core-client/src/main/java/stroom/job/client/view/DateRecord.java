package stroom.job.client.view;

import java.util.Objects;

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

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DateRecord that = (DateRecord) o;
        return year == that.year && month == that.month && day == that.day;
    }

    @Override
    public int hashCode() {
        return Objects.hash(year, month, day);
    }

    @Override
    public String toString() {
        return "DateRecord{" +
                "year=" + year +
                ", month=" + month +
                ", day=" + day +
                '}';
    }
}
