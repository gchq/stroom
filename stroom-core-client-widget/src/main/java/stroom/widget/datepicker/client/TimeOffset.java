package stroom.widget.datepicker.client;

import stroom.widget.util.client.ClientStringUtil;

import java.util.Objects;

public class TimeOffset {

    private final int hours;
    private final int minutes;

    public TimeOffset(final int hours, final int minutes) {
        this.hours = hours;
        this.minutes = minutes;
    }

    public int getHours() {
        return hours;
    }

    public int getMinutes() {
        return minutes;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TimeOffset offset = (TimeOffset) o;
        return hours == offset.hours && minutes == offset.minutes;
    }

    @Override
    public int hashCode() {
        return Objects.hash(hours, minutes);
    }

    @Override
    public String toString() {
        final String offset = ClientStringUtil.zeroPad(2, hours) + ClientStringUtil.zeroPad(2, minutes);
        if ((hours * 60) + minutes >= 0) {
            return "+" + offset;
        }
        return "-" + offset;
    }
}
