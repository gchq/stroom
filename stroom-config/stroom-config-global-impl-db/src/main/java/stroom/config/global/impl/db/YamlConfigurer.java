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

package stroom.config.global.impl.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.app.AppConfig;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@Singleton
class YamlConfigurer {
    //    private static final String USER_CONF_DIR = ".stroom";
    private static final Logger LOGGER = LoggerFactory.getLogger(YamlConfigurer.class);
//    private static final String USER_CONF_PATH = USER_CONF_DIR + "/stroom.yaml";

    YamlConfigurer() {
    }

    @Inject
    YamlConfigurer(final AppConfig appConfig) {
        try (final InputStream inputStream = getClass().getResourceAsStream("default.yaml")) {
            read(appConfig, inputStream);
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }

//        final String expected = new String(Files.readAllBytes(exampleFile));
//
//        // Get properties for the current user if there are any.
//        final Path file = Paths.get(System.getProperty("user.home") + "/" + USER_CONF_PATH);
//        if (Files.isRegularFile(file)) {
//            try (final InputStream inputStream = Files.newInputStream(file)) {
//                read(stroomConfig, inputStream);
//            } catch (final IOException e) {
//                LOGGER.error(e.getMessage(), e);
//            }
//        } else {
//            // If no config file exists then dump put all of the config.
//            try (final OutputStream outputStream = Files.newOutputStream(file)) {
//                write(stroomConfig, outputStream);
//
//            } catch (final IOException e) {
//                LOGGER.error(e.getMessage(), e);
//            }
//        }
    }

    void read(final AppConfig appConfig, final InputStream inputStream) throws IOException {
        final YAMLFactory yf = new YAMLFactory();
        final ObjectMapper mapper = new ObjectMapper(yf);
        final AppConfig config = mapper.readerFor(AppConfig.class).readValue(inputStream);
        FieldMapper.copy(config, appConfig);
    }

    void write(final AppConfig appConfig, final OutputStream outputStream) throws IOException {
        final YAMLFactory yf = new YAMLFactory();
        final ObjectMapper mapper = new ObjectMapper(yf);
        mapper.writeValue(outputStream, appConfig);
    }
}
