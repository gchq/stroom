package stroom.event.logging.mock;

import stroom.entity.shared.ExpressionCriteria;
import stroom.event.logging.api.StroomEventLoggingService;

import event.logging.BaseObject;
import event.logging.ComplexLoggedOutcome;
import event.logging.ComplexLoggedSupplier;
import event.logging.Criteria;
import event.logging.Data;
import event.logging.Event;
import event.logging.EventAction;
import event.logging.EventLoggingService;
import event.logging.LoggedWorkExceptionHandler;
import event.logging.Purpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Supplier;

public class MockStroomEventLoggingService implements EventLoggingService, StroomEventLoggingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockStroomEventLoggingService.class);

//    @Override
//    public Event createSkeletonEvent(final String typeId, final String description) {
//        return Event.builder()
//                .withEventDetail(EventDetail.builder()
//                        .withTypeId(typeId)
//                        .withDescription(description)
//                        .build())
//                .build();
//    }
//
//    @Override
//    public Event createSkeletonEvent(final String typeId,
//                                     final String description,
//                                     final Consumer<Builder<Void>> eventDetailBuilderConsumer) {
//        final EventDetail.Builder<Void> eventDetailBuilder = EventDetail.builder()
//                .withTypeId(typeId)
//                .withDescription(description);
//
//        if (eventDetailBuilderConsumer != null) {
//            eventDetailBuilderConsumer.accept(eventDetailBuilder);
//        }
//
//        return buildEvent()
//                .withEventDetail(eventDetailBuilder.build())
//                .build();
//    }

    @Override
    public void log(final Event event) {
        LOGGER.info("log called for event {}", event);
    }

    @Override
    public void log(final String typeId,
                    final String description,
                    final EventAction eventAction) {
        LOGGER.info("log called for typeId {}, description {}", typeId, description);
    }

    @Override
    public void log(final String typeId,
                    final String description,
                    final Purpose purpose,
                    final EventAction eventAction) {
        LOGGER.info("log called for typeId {}, description {}", typeId, description);
    }

    @Override
    public <T_RESULT, T_EVENT_ACTION extends EventAction> T_RESULT loggedResult(
            final String eventTypeId,
            final String description,
            final Purpose purpose,
            final T_EVENT_ACTION eventAction,
            final ComplexLoggedSupplier<T_RESULT, T_EVENT_ACTION> loggedWork,
            final LoggedWorkExceptionHandler<T_EVENT_ACTION> exceptionHandler) {

        LOGGER.info("loggedResult called for typeId {}, description {}",
                eventTypeId, description);

        try {
            final ComplexLoggedOutcome<T_RESULT, T_EVENT_ACTION> loggedResult = loggedWork.get(eventAction);
            return loggedResult.getResult();
        } catch (Exception e) {
            if (exceptionHandler != null) {
                exceptionHandler.handle(eventAction, e);
            }
            throw e;
        }
    }


    @Override
    public void setValidate(final Boolean validate) {

    }

    @Override
    public boolean isValidate() {
        return false;
    }

    @Override
    public BaseObject convert(final Object object, final boolean useInfoProviders) {
        return null;
    }

    @Override
    public BaseObject convert(final Object object) {
        return null;
    }

    @Override
    public BaseObject convert(final Supplier<?> objectSupplier, final boolean useInfoProviders) {
        return null;
    }

    @Override
    public BaseObject convert(final Supplier<?> objectSupplier) {
        return null;
    }

    @Override
    public Criteria convertExpressionCriteria(final String type, final ExpressionCriteria expressionCriteria) {
        return null;
    }

    @Override
    public String describe(final Object object) {
        return null;
    }

    @Override
    public List<Data> getDataItems(final Object obj) {
        return null;
    }
}
