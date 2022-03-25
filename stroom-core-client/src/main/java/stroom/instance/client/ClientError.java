package stroom.instance.client;

import stroom.alert.client.event.AlertEvent;
import stroom.util.client.Console;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ClientError implements HasHandlers {

    private final EventBus eventBus;

    @Inject
    public ClientError(final EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void error(final String message) {
        Console.log("Error: " + message);
        AlertEvent.fireError(this,
                message,
                null);
    }

    public void error(final String message, final String description) {
        Console.log("Error: " + message + "\n" + description);
        AlertEvent.fireError(this,
                message,
                description,
                null);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
