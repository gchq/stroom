/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.proxy.app;

import stroom.util.concurrent.LazyValue;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.yaml.YamlUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.ConfigurationFactory;
import io.dropwizard.configuration.ConfigurationFactoryFactory;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.configuration.DefaultConfigurationFactoryFactory;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.FileConfigurationSourceProvider;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.jackson.Jackson;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

public class ProxyYamlUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyYamlUtil.class);

    private static final LazyValue<ConfigurationFactory<Config>> configFactorySupplier = LazyValue.initialisedBy(
            ProxyYamlUtil::createConfigFactory);

    private ProxyYamlUtil() {
        // Utility
    }

    private static ConfigurationFactory<Config> createConfigFactory() {
        final ConfigurationFactoryFactory<Config> configurationFactoryFactory =
                new DefaultConfigurationFactoryFactory<>();

        // Jackson.newObjectMapper() is a special dropwiz configured ObjectMapper that includes
        // YamlFactory and registers Jdk8Module so DON'T use one from YamlUtil
        return configurationFactoryFactory
                .create(
                        Config.class,
                        io.dropwizard.jersey.validation.Validators.newValidator(),
                        Jackson.newObjectMapper(),
                        "dw");
    }

    public static ProxyConfig readProxyConfig(final Path configFile) throws IOException {
        return readConfig(configFile).getProxyConfig();
    }

    /**
     * Reads a yaml file that matches the structure of a complete DropWizard {@link Config} object tree.
     *
     * @throws IOException
     */
    public static Config readConfig(final Path configFile) throws IOException {

        final ConfigurationSourceProvider configurationSourceProvider = createConfigurationSourceProvider(
                new FileConfigurationSourceProvider(), false);

        final ConfigurationFactory<Config> configurationFactory = configFactorySupplier.getValueWithLocks();

        Config config = null;
        try {
            config = configurationFactory.build(configurationSourceProvider, configFile.toAbsolutePath().toString());
        } catch (final ConfigurationException e) {
            throw new RuntimeException(LogUtil.message("Error parsing configuration file {}\n{}",
                    configFile.toAbsolutePath(), e.getMessage()), e);
        }

        return config;
    }

    public static ConfigurationSourceProvider createConfigurationSourceProvider(
            final ConfigurationSourceProvider baseConfigurationSourceProvider,
            final boolean logChanges) {

        return new ProxyConfigurationSourceProvider(
                new SubstitutingSourceProvider(
                        baseConfigurationSourceProvider,
                        new EnvironmentVariableSubstitutor(false)), logChanges);
    }

    public static void writeConfig(final Config config, final OutputStream outputStream) throws IOException {
        final ObjectMapper mapper = YamlUtil.getVanillaObjectMapper();
        // wrap the AppConfig so that it sits at the right level
        mapper.writeValue(outputStream, config);

    }

    public static void writeConfig(final ProxyConfig proxyConfig, final OutputStream outputStream) throws IOException {
        final Config config = new Config();
        config.setProxyConfig(proxyConfig);
        writeConfig(config, outputStream);
    }

    public static void writeConfig(final Config config, final Path path) throws IOException {
        final ObjectMapper mapper = YamlUtil.getVanillaObjectMapper();
        // wrap the AppConfig so that it sits at the right level
        mapper.writeValue(path.toFile(), config);
    }

    public static void writeConfig(final ProxyConfig proxyConfig, final Path path) throws IOException {
        final Config config = new Config();
        config.setProxyConfig(proxyConfig);
        writeConfig(config, path);
    }
}
