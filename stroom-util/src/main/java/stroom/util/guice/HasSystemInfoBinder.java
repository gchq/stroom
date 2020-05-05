package stroom.util.guice;

import stroom.util.sysinfo.HasSystemInfo;

import com.google.inject.Binder;
import com.google.inject.multibindings.Multibinder;

public class HasSystemInfoBinder {
    private final Multibinder<HasSystemInfo> multibinder;

    private HasSystemInfoBinder(final Binder binder) {
        multibinder = Multibinder.newSetBinder(binder, HasSystemInfo.class);
    }

    public static HasSystemInfoBinder create(final Binder binder) {
        return new HasSystemInfoBinder(binder);
    }

    public <H extends HasSystemInfo> HasSystemInfoBinder bind(final Class<H> hasSystemInfoClass) {
        multibinder.addBinding().to(hasSystemInfoClass);
        return this;
    }
}
