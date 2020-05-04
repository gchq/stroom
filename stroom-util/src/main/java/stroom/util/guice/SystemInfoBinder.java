package stroom.util.guice;

import stroom.util.sysinfo.HasSystemInfo;

import com.google.inject.Binder;
import com.google.inject.multibindings.Multibinder;

public class SystemInfoBinder {
    private final Multibinder<HasSystemInfo> multibinder;

    private SystemInfoBinder(final Binder binder) {
        multibinder = Multibinder.newSetBinder(binder, HasSystemInfo.class);
    }

    public static SystemInfoBinder create(final Binder binder) {
        return new SystemInfoBinder(binder);
    }

    public <H extends HasSystemInfo> SystemInfoBinder bind(final Class<H> hasSystemInfoClass) {
        multibinder.addBinding().to(hasSystemInfoClass);
        return this;
    }
}
