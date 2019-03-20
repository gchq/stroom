package stroom.util.guice;

import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;

import javax.servlet.Servlet;

public class ServletBinder {
    private final MapBinder<ServletInfo, Servlet> mapBinder;

    private ServletBinder(final Binder binder) {
        mapBinder = MapBinder.newMapBinder(binder, ServletInfo.class, Servlet.class);
    }

    public static ServletBinder create(final Binder binder) {
        return new ServletBinder(binder);
    }

    public <H extends Servlet> ServletBinder bind(final String path, final Class<H> servletClass) {
        mapBinder.addBinding(new ServletInfo(path)).to(servletClass);
        return this;
    }
}
