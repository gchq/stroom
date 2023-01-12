package stroom.suggestions.api;

import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;

public final class SuggestionsServiceBinder {

    private final MapBinder<String, SuggestionsQueryHandler> mapBinder;

    private SuggestionsServiceBinder(final Binder binder) {
        mapBinder = MapBinder.newMapBinder(binder, String.class, SuggestionsQueryHandler.class);
    }

    public static SuggestionsServiceBinder create(final Binder binder) {
        return new SuggestionsServiceBinder(binder);
    }

    public <T extends SuggestionsQueryHandler> SuggestionsServiceBinder bind(
            final String dataSourceType,
            final Class<T> queryHandlerClass
    ) {
        mapBinder.addBinding(dataSourceType).to(queryHandlerClass);
        return this;
    }
}
