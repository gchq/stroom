package stroom.event.logging.mock;

import stroom.event.logging.api.StroomEventLoggingService;

import event.logging.Event;
import event.logging.EventAction;
import event.logging.EventDetail;
import event.logging.EventDetail.Builder;
import event.logging.EventLoggingService;
import event.logging.LoggedResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class MockStroomEventLoggingService implements EventLoggingService, StroomEventLoggingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockStroomEventLoggingService.class);

    @Override
    public Event createSkeletonEvent(final String typeId, final String description) {
        return Event.builder()
                .withEventDetail(EventDetail.builder()
                        .withTypeId(typeId)
                        .withDescription(description)
                        .build())
                .build();
    }

    @Override
    public Event createSkeletonEvent(final String typeId,
                                     final String description,
                                     final Consumer<Builder<Void>> eventDetailBuilderConsumer) {
        final EventDetail.Builder<Void> eventDetailBuilder = EventDetail.builder()
                .withTypeId(typeId)
                .withDescription(description);

        if (eventDetailBuilderConsumer != null) {
            eventDetailBuilderConsumer.accept(eventDetailBuilder);
        }

        return buildEvent()
                .withEventDetail(eventDetailBuilder.build())
                .build();
    }

    @Override
    public void log(final String typeId,
                    final String description,
                    final Consumer<Builder<Void>> eventDetailBuilderConsumer) {

        LOGGER.info("log called for typeId {}, description {}", typeId, description);
    }

    @Override
    public <T_RESULT, T_EVENT_ACTION extends EventAction> T_RESULT loggedResult(
            final String eventTypeId,
            final String description,
            final T_EVENT_ACTION eventAction,
            final Function<T_EVENT_ACTION, LoggedResult<T_RESULT, T_EVENT_ACTION>> loggedWork,
            final BiFunction<T_EVENT_ACTION, Throwable, T_EVENT_ACTION> exceptionHandler) {

        LOGGER.info("loggedResult called for typeId {}, description {}",
                eventTypeId, description);

        try {
            final LoggedResult<T_RESULT, T_EVENT_ACTION> loggedResult = loggedWork.apply(eventAction);
            return loggedResult.getResult();
        } catch (Exception e) {
            if (exceptionHandler != null) {
                exceptionHandler.apply(eventAction, e);
            }
            throw e;
        }
    }

    @Override
    public void log(final Event event) {
        LOGGER.info("log called for event {}", event);
    }

    @Override
    public void setValidate(final Boolean validate) {

    }

    @Override
    public boolean isValidate() {
        return false;
    }
}
