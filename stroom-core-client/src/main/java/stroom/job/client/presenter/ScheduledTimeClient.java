package stroom.job.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestFactory;
import stroom.job.shared.GetScheduledTimesRequest;
import stroom.job.shared.ScheduledTimeResource;
import stroom.job.shared.ScheduledTimes;
import stroom.util.shared.scheduler.Schedule;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

public class ScheduledTimeClient implements HasHandlers {

    private static final ScheduledTimeResource SCHEDULED_TIME_RESOURCE = GWT.create(ScheduledTimeResource.class);

    private final EventBus eventBus;
    private final RestFactory restFactory;

    @Inject
    public ScheduledTimeClient(final EventBus eventBus, final RestFactory restFactory) {
        this.eventBus = eventBus;
        this.restFactory = restFactory;
    }

    public void validate(final Schedule schedule, final Consumer<ScheduledTimes> consumer) {
        final GetScheduledTimesRequest request = new GetScheduledTimesRequest(schedule, null, null);
        getScheduledTimes(request, consumer);
    }

    public void getScheduledTimes(final GetScheduledTimesRequest request,
                                  final Consumer<ScheduledTimes> consumer) {
        try {
            validateExpression(request.getSchedule());
            restFactory
                    .builder()
                    .forType(ScheduledTimes.class)
                    .onSuccess(consumer)
                    .call(SCHEDULED_TIME_RESOURCE)
                    .get(request);
        } catch (final RuntimeException e) {
            AlertEvent.fireWarn(
                    this,
                    e.getMessage(),
                    null);
        }
    }

    private void validateExpression(final Schedule schedule) {
        if (schedule == null) {
            throw new RuntimeException("No schedule has been set");
        }
        if (schedule.getType() == null) {
            throw new RuntimeException("Schedule type has not been set");
        }
        if (schedule.getExpression() == null || schedule.getExpression().length() == 0) {
            throw new RuntimeException("Schedule expression has not been set");
        }
        final String[] expressionParts = schedule.getExpression().split(" ");
        if (expressionParts.length > 0 && expressionParts[0].equals("*")) {
            throw new RuntimeException("You cannot execute every second");
        } else if (expressionParts.length > 1 && expressionParts[1].equals("*")) {
            throw new RuntimeException("You cannot execute every minute");
        }
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        eventBus.fireEvent(gwtEvent);
    }
}
