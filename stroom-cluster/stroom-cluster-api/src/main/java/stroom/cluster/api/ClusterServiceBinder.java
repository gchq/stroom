package stroom.cluster.api;

import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;

public class ClusterServiceBinder {
    private final MapBinder<ServiceName, Object> mapBinder;

    private ClusterServiceBinder(final Binder binder) {
        mapBinder = MapBinder.newMapBinder(binder, ServiceName.class, Object.class);
    }

    public static ClusterServiceBinder create(final Binder binder) {
        return new ClusterServiceBinder(binder);
    }

    public ClusterServiceBinder bind(final ServiceName name, final Class<?> handler) {
        mapBinder.addBinding(name).to(handler);
        return this;
    }
}
