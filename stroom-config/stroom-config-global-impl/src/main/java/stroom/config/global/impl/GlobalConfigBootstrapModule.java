package stroom.config.global.impl;

import stroom.util.validation.ValidationModule;

import com.google.inject.AbstractModule;

public class GlobalConfigBootstrapModule extends AbstractModule {

    @Override
    protected void configure() {

//        install(new ConfigProvidersModule());
        install(new ValidationModule());

        // Need to ensure it initialises so any db props can be set on AppConfig
        bind(GlobalConfigBootstrapService.class).asEagerSingleton();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
