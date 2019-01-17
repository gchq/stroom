package stroom.event.logging.api;

import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;

public class ObjectInfoProviderBinder {
    private final MapBinder<ObjectType, ObjectInfoProvider> mapBinder;

    private ObjectInfoProviderBinder(final Binder binder) {
        mapBinder = MapBinder.newMapBinder(binder, ObjectType.class, ObjectInfoProvider.class);
    }

    public static ObjectInfoProviderBinder create(final Binder binder) {
        return new ObjectInfoProviderBinder(binder);
    }

    public <H extends ObjectInfoProvider> ObjectInfoProviderBinder bind(final Class<?> task, final Class<H> handler) {
        mapBinder.addBinding(new ObjectType(task)).to(handler);
        return this;
    }
}
