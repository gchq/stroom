package stroom.job.api;

import stroom.job.api.Schedule.ScheduleType;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestSchedule {

    @Test
    void testCron() {
        Schedule schedule = Schedule.cronSchedule()
                .withMinutes(1)
                .withHours(2)
                .withDays(3)
                .build();
        Assertions.assertThat(schedule.getScheduleType())
                .isEqualTo(ScheduleType.CRON);
        Assertions.assertThat(schedule.getSchedule())
                .isEqualTo("1 2 3");

        schedule = Schedule.cronSchedule()
                .withMinutes(10, 20)
                .withHours(2, 3)
                .withDays(4, 5)
                .build();
        Assertions.assertThat(schedule.getScheduleType())
                .isEqualTo(ScheduleType.CRON);
        Assertions.assertThat(schedule.getSchedule())
                .isEqualTo("10,20 2,3 4,5");

        schedule = Schedule.cronSchedule()
                .everyMinute()
                .everyHour()
                .everyDay()
                .build();
        Assertions.assertThat(schedule.getScheduleType())
                .isEqualTo(ScheduleType.CRON);
        Assertions.assertThat(schedule.getSchedule())
                .isEqualTo("* * *");

        schedule = Schedule.cronSchedule()
                .everyMinute()
                .withHours(1)
                .everyDay()
                .build();
        Assertions.assertThat(schedule.getScheduleType())
                .isEqualTo(ScheduleType.CRON);
        Assertions.assertThat(schedule.getSchedule())
                .isEqualTo("* 1 *");

        Assertions.assertThatThrownBy(() -> {
            Schedule.cronSchedule()
                    .build();
        });

        Assertions.assertThatThrownBy(() -> {
            Schedule.cronSchedule()
                    .everyMinute()
                    .everyHour()
                    .build();
        });
    }

    @Test
    void testPeriodic() {
        Schedule schedule = Schedule.periodicSchedule(500);
        Assertions.assertThat(schedule.getScheduleType())
                .isEqualTo(ScheduleType.PERIODIC);
        Assertions.assertThat(schedule.getSchedule())
                .isEqualTo("500");

        schedule = Schedule.periodicSchedule(5, "d");
        Assertions.assertThat(schedule.getScheduleType())
                .isEqualTo(ScheduleType.PERIODIC);
        Assertions.assertThat(schedule.getSchedule())
                .isEqualTo("5d");
    }
}
