package stroom.util.entity;

import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;

public class EntityTypeBinder {
    private final MapBinder<EntityType, Object> mapBinder;

    private EntityTypeBinder(final Binder binder) {
        mapBinder = MapBinder.newMapBinder(binder, EntityType.class, Object.class);
    }

    public static EntityTypeBinder create(final Binder binder) {
        return new EntityTypeBinder(binder);
    }

    public EntityTypeBinder bind(final String name, final Class<?> handler) {
        mapBinder.addBinding(new EntityType(name)).to(handler);
        return this;
    }
}
