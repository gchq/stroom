package stroom.event.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.Event;
import com.google.web.bindery.event.shared.EventBus;

/**
 * Somewhat hacky class to allow us to fire events from composites.
 */
@Singleton
public class StaticEventBus {

    private static EventBus eventBus = null;

    @Inject
    public StaticEventBus(final EventBus eventBus) {
        StaticEventBus.eventBus = eventBus;
    }

    public static EventBus getEventBus() {
        if (eventBus == null) {
            throw new RuntimeException("Static eventBus has not been initialised");
        }
        return eventBus;
    }

    public static <T> void fire(final Event<T> event) {
        getEventBus().fireEvent(event);
    }
}
