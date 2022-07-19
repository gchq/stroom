package stroom.job.api;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Schedule {

    private final String schedule;
    private final ScheduleType scheduleType;

    public Schedule(final ScheduleType scheduleType, final String schedule) {
        this.schedule = schedule;
        this.scheduleType = scheduleType;
    }

    public static CronBuilder cronSchedule() {
        return new CronBuilder();
    }

    public static Schedule periodicSchedule(final int milliseconds) {
        return new Schedule(ScheduleType.PERIODIC, Integer.toString(milliseconds));
    }

    public static Schedule periodicSchedule(final int value, final String unit) {
        Objects.requireNonNull(unit);
        return new Schedule(ScheduleType.PERIODIC, value + unit.trim());
    }

    public String getSchedule() {
        return schedule;
    }

    public ScheduleType getScheduleType() {
        return scheduleType;
    }

    public enum ScheduleType {
        CRON,
        PERIODIC
    }

    public static class CronBuilder {
        private String minutePart = null;
        private String hourPart = null;
        private String dayPart = null;

        public CronBuilder withMinutes(final int... minutes) {
            this.minutePart = convertValues(minutes);
            return this;
        }

        public CronBuilder everyMinute() {
            this.minutePart = "*";
            return this;
        }

        public CronBuilder withHours(final int... hours) {
            this.hourPart = convertValues(hours);
            return this;
        }

        public CronBuilder everyHour() {
            this.hourPart = "*";
            return this;
        }

        public CronBuilder withDays(final int... days) {
            this.dayPart = convertValues(days);
            return this;
        }

        public CronBuilder everyDay() {
            this.dayPart = "*";
            return this;
        }

        private String convertValues(final int... values) {
            Objects.requireNonNull(values);
            if (values.length == 1) {
                return Integer.toString(values[0]);
            } else {
                return IntStream.of(values)
                        .boxed()
                        .map(Object::toString)
                        .collect(Collectors.joining(","));
            }
        }

        public Schedule build() {
            Objects.requireNonNull(minutePart);
            Objects.requireNonNull(hourPart);
            Objects.requireNonNull(dayPart);
            return new Schedule(
                    ScheduleType.CRON,
                    String.join(" ", minutePart, hourPart, dayPart));
        }
    }
}
