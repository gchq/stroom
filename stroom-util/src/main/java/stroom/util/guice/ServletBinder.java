package stroom.util.guice;

import stroom.util.shared.IsServlet;

import com.google.inject.Binder;
import com.google.inject.multibindings.Multibinder;

public class ServletBinder {
    private final Multibinder<IsServlet> multibinder;

    private ServletBinder(final Binder binder) {
        multibinder = Multibinder.newSetBinder(binder, IsServlet.class);
    }

    public static ServletBinder create(final Binder binder) {
        return new ServletBinder(binder);
    }

    public <H extends IsServlet> ServletBinder bind(final Class<H> servletClass) {
        multibinder.addBinding().to(servletClass);
        return this;
    }
}
