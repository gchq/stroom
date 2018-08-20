package stroom.properties.global.impl.db;

import javax.inject.Inject;
import javax.inject.Provider;

public class ConfigInitialiser {
    @Inject
    ConfigInitialiser(final Provider<ConfigMapper> configMapperProvider,
                      final Provider<GlobalPropertyService> globalPropertyServiceProvider) {
        // The order these services are initialised is important.
        configMapperProvider.get();
        globalPropertyServiceProvider.get();
    }
}
