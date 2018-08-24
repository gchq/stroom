package stroom.config.global.impl.db;

import javax.inject.Inject;
import javax.inject.Provider;

class ConfigInitialiser {
    @Inject
    ConfigInitialiser(final Provider<YamlConfigurer> yamlConfigurerProvider,
                      final Provider<ConfigMapper> configMapperProvider,
                      final Provider<GlobalConfigService> globalPropertyServiceProvider) {
        // The order these services are initialised is important.
        yamlConfigurerProvider.get();
        configMapperProvider.get();
        globalPropertyServiceProvider.get();
    }
}
