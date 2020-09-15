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

package stroom.proxy.dist;

import stroom.proxy.app.Config;
import stroom.proxy.app.ProxyConfig;
import stroom.util.logging.LogUtil;

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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

public class ProxyYamlUtil {

    private ProxyYamlUtil() {
        // Utility
    }

    public static ProxyConfig readAppConfig(final Path configFile) throws IOException {
        return readConfig(configFile).getProxyConfig();
    }

    /**
     * Reads a yaml file that matches the structure of a complete DropWizard {@link Config} object tree.
     * @throws IOException
     */
    public static Config readConfig(final Path configFile) throws IOException {
        final ConfigurationSourceProvider configurationSourceProvider = new SubstitutingSourceProvider(
                new FileConfigurationSourceProvider(),
                new EnvironmentVariableSubstitutor(false));

        final ConfigurationFactoryFactory<Config> configurationFactoryFactory = new DefaultConfigurationFactoryFactory<>();

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

    public static void writeConfig(final Config config, final OutputStream outputStream) throws IOException {
        final YAMLFactory yf = new YAMLFactory();
        final ObjectMapper mapper = new ObjectMapper(yf);
        // wrap the AppConfig so that it sits at the right level
        mapper.writeValue(outputStream, config);

    }
    public static void writeConfig(final ProxyConfig proxyConfig, final OutputStream outputStream) throws IOException {
        Config config = new Config();
        config.setProxyConfig(proxyConfig);
        writeConfig(config, outputStream);
    }

    public static void writeConfig(final Config config, final Path path) throws IOException {
        final YAMLFactory yf = new YAMLFactory();
        final ObjectMapper mapper = new ObjectMapper(yf);
        // wrap the AppConfig so that it sits at the right level
        mapper.writeValue(path.toFile(), config);
    }

    public static void writeConfig(final ProxyConfig proxyConfig, final Path path) throws IOException {
        Config config = new Config();
        config.setProxyConfig(proxyConfig);
        writeConfig(config, path);
    }
}
