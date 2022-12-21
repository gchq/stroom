/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.config.app;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.ConfigurationFactory;
import io.dropwizard.configuration.ConfigurationFactoryFactory;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.configuration.DefaultConfigurationFactoryFactory;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.FileConfigurationSourceProvider;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.jackson.Jackson;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public class StroomYamlUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomYamlUtil.class);

    private StroomYamlUtil() {
        // Utility
    }


    public static AppConfig readAppConfig(final Path configFile) throws IOException {
        return readConfig(configFile).getYamlAppConfig();
    }

    /**
     * Reads a yaml file that matches the structure of a complete DropWizard {@link Config}
     * object tree. The file undergoes substitution and validation.
     */
    public static Config readConfig(final Path configFile) throws IOException {

        final ConfigurationSourceProvider configurationSourceProvider = createConfigurationSourceProvider(
                new FileConfigurationSourceProvider(), false);

        final ConfigurationFactoryFactory<Config> configurationFactoryFactory =
                new DefaultConfigurationFactoryFactory<>();

        final ConfigurationFactory<Config> configurationFactory = configurationFactoryFactory
                .create(
                        Config.class,
                        io.dropwizard.jersey.validation.Validators.newValidator(),
                        Jackson.newObjectMapper(),
                        "dw");

        Config config = null;
        try {
            config = configurationFactory.build(configurationSourceProvider, configFile.toAbsolutePath().toString());
        } catch (ConfigurationException e) {
            throw new RuntimeException(LogUtil.message("Error parsing configuration from file {}",
                    configFile.toAbsolutePath()), e);
        }

        return config;
    }

    public static ConfigurationSourceProvider createConfigurationSourceProvider(
            final ConfigurationSourceProvider baseConfigurationSourceProvider,
            final boolean logChanges) {

        return new StroomConfigurationSourceProvider(
                new SubstitutingSourceProvider(
                        baseConfigurationSourceProvider,
                        new EnvironmentVariableSubstitutor(false)),
                logChanges);
    }

    /**
     * Reads a YAML string that has already been through the drop wizard env var substitution.
     */
    public static AppConfig readDropWizardSubstitutedAppConfig(final String yamlStr) {

        Objects.requireNonNull(yamlStr);

        final Yaml yaml = new Yaml();
        final Map<String, Object> obj = yaml.load(yamlStr);

        // fail on unknown so it skips over all the drop wiz yaml content that has no
        // corresponding annotated props in DummyConfig
        final ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        try {
            final DummyConfig dummyConfig = mapper.convertValue(obj, DummyConfig.class);
            return dummyConfig.getAppConfig();
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Error parsing yaml string", e);
        }
    }

    public static void writeConfig(final Config config, final OutputStream outputStream) throws IOException {
        final YAMLFactory yf = new YAMLFactory();
        final ObjectMapper mapper = new ObjectMapper(yf);
        // wrap the AppConfig so that it sits at the right level
        mapper.writeValue(outputStream, config);

    }

    public static void writeConfig(final AppConfig appConfig, final OutputStream outputStream) throws IOException {
        Config config = new Config();
        config.setYamlAppConfig(appConfig);
        writeConfig(config, outputStream);
    }

    public static void writeConfig(final Config config, final Path path) throws IOException {
        final YAMLFactory yf = new YAMLFactory();
        final ObjectMapper mapper = new ObjectMapper(yf);
        mapper.writeValue(path.toFile(), config);
    }

    public static void writeConfig(final AppConfig appConfig, final Path path) throws IOException {
        Config config = new Config();
        config.setYamlAppConfig(appConfig);
        writeConfig(config, path);
    }

    /**
     * Writes {@link AppConfig} to the file as if appConfig was the only property of a wrapper object.
     * This is essentially a DropWizard configuration file without the DropWizard bits.
     */
    public static void writeAppConfig(final AppConfig appConfig, final Path path) throws IOException {
        final DummyConfig config = new DummyConfig(appConfig);
        final YAMLFactory yf = new YAMLFactory();
        final ObjectMapper mapper = new ObjectMapper(yf);
        mapper.writeValue(path.toFile(), config);
    }


    /**
     * Used to simulate the {@link Config} class that wraps {@link AppConfig} when we are not
     * interested in anything in {@link Config} except {@link AppConfig}.
     */
    private static class DummyConfig {

        @JsonProperty("appConfig")
        private final AppConfig appConfig;

        @JsonCreator
        public DummyConfig(@JsonProperty("appConfig") final AppConfig appConfig) {
            this.appConfig = appConfig;
        }

        public AppConfig getAppConfig() {
            return appConfig;
        }
    }
}
