package stroom.logging;

import com.google.inject.Binder;
import com.google.inject.multibindings.Multibinder;

public class EventInforProviderBinder {
    private final Multibinder<EventInfoProvider> multibinder;

    private EventInforProviderBinder(final Binder binder) {
        multibinder = Multibinder.newSetBinder(binder, EventInfoProvider.class);
    }

    public static EventInforProviderBinder create(final Binder binder) {
        return new EventInforProviderBinder(binder);
    }

    public <T extends EventInfoProvider> EventInforProviderBinder bind(final Class<T> eventInfoProvider) {
        multibinder.addBinding().to(eventInfoProvider);
        return this;
    }
}
