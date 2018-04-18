package stroom.util;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class ToolInjector {
    private Injector injector = null;

    Injector getInjector() {
        if (injector == null) {
            injector = createInjector();
        }
        return injector;
    }

    private Injector createInjector() {
        final Injector injector = Guice.createInjector(new ToolModule());
        injector.injectMembers(this);

        return injector;
    }
}
