package stroom.schedule.client;

import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.job.shared.GetScheduledTimesRequest;
import stroom.job.shared.ScheduleRestriction;
import stroom.job.shared.ScheduledTimeResource;
import stroom.job.shared.ScheduledTimes;
import stroom.task.client.TaskHandlerFactory;
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

    public void validate(final Schedule schedule,
                         final ScheduleRestriction scheduleRestriction,
                         final Consumer<ScheduledTimes> consumer,
                         final TaskHandlerFactory taskHandlerFactory) {
        final GetScheduledTimesRequest request =
                new GetScheduledTimesRequest(schedule, null, scheduleRestriction);
        getScheduledTimes(request, consumer, taskHandlerFactory);
    }

    public void getScheduledTimes(final GetScheduledTimesRequest request,
                                  final Consumer<ScheduledTimes> consumer,
                                  final TaskHandlerFactory taskHandlerFactory) {
        restFactory
                .create(SCHEDULED_TIME_RESOURCE)
                .method(res -> res.get(request))
                .onSuccess(consumer)
                .onFailure(new DefaultErrorHandler(this, () -> consumer.accept(null)))
                .taskHandlerFactory(taskHandlerFactory)
                .exec();
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        eventBus.fireEvent(gwtEvent);
    }
}
