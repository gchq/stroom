package stroom.util.guice;

import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;

import javax.servlet.Filter;

public class FilterBinder {
    private final MapBinder<FilterInfo, Filter> mapBinder;

    private FilterBinder(final Binder binder) {
        mapBinder = MapBinder.newMapBinder(binder, FilterInfo.class, Filter.class);
    }

    public static FilterBinder create(final Binder binder) {
        return new FilterBinder(binder);
    }

    public <H extends Filter> FilterBinder bind(final FilterInfo filterInfo, final Class<H> servletClass) {
        mapBinder.addBinding(filterInfo).to(servletClass);
        return this;
    }
}
