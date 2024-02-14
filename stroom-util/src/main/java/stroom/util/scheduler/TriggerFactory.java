package stroom.util.scheduler;

import stroom.util.shared.scheduler.Schedule;

public class TriggerFactory {

    private TriggerFactory() {
        // Ignore.
    }

    public static Trigger create(final Schedule schedule) {
        if (schedule == null || schedule.getType() == null) {
            throw new RuntimeException("Null schedule");
        }
        return switch (schedule.getType()) {
            case INSTANT -> new InstantTrigger();
            case FREQUENCY -> new FrequencyTrigger(schedule.getExpression());
            case CRON -> new CronTrigger(schedule.getExpression());
        };
    }
}
