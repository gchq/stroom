package stroom.util.guice;

import stroom.util.shared.IsAdminServlet;

import com.google.inject.Binder;
import com.google.inject.multibindings.Multibinder;

public class AdminServletBinder {

    private final Multibinder<IsAdminServlet> multibinder;

    private AdminServletBinder(final Binder binder) {
        multibinder = Multibinder.newSetBinder(binder, IsAdminServlet.class);
    }

    public static AdminServletBinder create(final Binder binder) {
        return new AdminServletBinder(binder);
    }

    public <H extends IsAdminServlet> AdminServletBinder bind(final Class<H> servletClass) {
        multibinder.addBinding().to(servletClass);
        return this;
    }
}
