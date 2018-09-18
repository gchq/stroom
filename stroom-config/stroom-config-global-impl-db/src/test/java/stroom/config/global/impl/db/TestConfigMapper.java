package stroom.config.global.impl.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.Configuration;
import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.ConfigurationFactory;
import io.dropwizard.configuration.ConfigurationFactoryFactory;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.configuration.DefaultConfigurationFactoryFactory;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.FileConfigurationSourceProvider;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.jackson.Jackson;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.app.AppConfig;
import stroom.config.global.api.ConfigProperty;

import javax.validation.Validator;
import java.io.IOException;
import java.util.List;

class TestConfigMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestConfigMapper.class);

    @Test
    void getGlobalProperties() throws IOException, ConfigurationException {

        AppConfig appConfig = getAppConfig();
        ConfigMapper configMapper = new ConfigMapper(appConfig);

        List<ConfigProperty> configProperties = configMapper.getGlobalProperties();

        configProperties.forEach(configProperty ->
                LOGGER.debug("{} - {}", configProperty.getName(), configProperty.getValue()));
    }

    @Test
    void update_string() throws IOException, ConfigurationException {
        AppConfig appConfig = getAppConfig();

        String initialValue = appConfig.getCoreConfig().getTemp();
        String newValue = initialValue + "/xxx";

        ConfigMapper configMapper = new ConfigMapper(appConfig);
        configMapper.update("stroom.core.temp", newValue);

        Assertions.assertThat(appConfig.getCoreConfig().getTemp()).isEqualTo(newValue);
    }

    @Test
    void update_boolean() throws IOException, ConfigurationException {
        AppConfig appConfig = getAppConfig();

        boolean initialValue = appConfig.getRefDataStoreConfig().isReadAheadEnabled();
        boolean newValue = !initialValue;

        ConfigMapper configMapper = new ConfigMapper(appConfig);
        configMapper.update("stroom.refdata.readAheadEnabled", Boolean.valueOf(newValue).toString().toLowerCase());

        Assertions.assertThat(appConfig.getRefDataStoreConfig().isReadAheadEnabled()).isEqualTo(newValue);
    }



    private AppConfig getAppConfig() throws IOException, ConfigurationException{
        ConfigurationSourceProvider configurationSourceProvider = new SubstitutingSourceProvider(
                new FileConfigurationSourceProvider(),
                new EnvironmentVariableSubstitutor(false));

            Config config = parseConfiguration(
                    new DefaultConfigurationFactoryFactory<>(),
                    configurationSourceProvider,
                    io.dropwizard.jersey.validation.Validators.newValidator(),
//                    "../../stroom-app/dev.yml",
                    "../../local.yml",
                    Config.class,
                    Jackson.newObjectMapper());

           return config.getAppConfig();
    }

    private Config parseConfiguration(ConfigurationFactoryFactory<Config> configurationFactoryFactory,
                                 ConfigurationSourceProvider provider,
                                 Validator validator,
                                 String path,
                                 Class<Config> klass,
                                 ObjectMapper objectMapper) throws IOException, ConfigurationException {
        final ConfigurationFactory<Config> configurationFactory = configurationFactoryFactory
                .create(klass, validator, objectMapper, "dw");
        if (path != null) {
            return configurationFactory.build(provider, path);
        }
        return configurationFactory.build();
    }

    private static class Config extends Configuration {
        private AppConfig appConfig;

        AppConfig getAppConfig() {
            return appConfig;
        }

        void setAppConfig(final AppConfig appConfig) {
            this.appConfig = appConfig;
        }
    }
}