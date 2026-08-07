/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.util.yaml;


import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class YamlFileUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(YamlFileUtil.class);

    private YamlFileUtil() {
    }

    public static Path getYamlFileFromArgs(final String[] args) {
        // This is not ideal as we are duplicating what dropwizard is doing but there appears to be
        // no way of getting the yaml file location from the dropwizard classes
        Path path = null;

        for (final String arg : args) {
            if (arg.toLowerCase().endsWith("yml") || arg.toLowerCase().endsWith("yaml")) {
                final Path yamlFile = Path.of(arg);
                if (Files.isRegularFile(yamlFile)) {
                    path = yamlFile;
                    break;
                } else {
                    // NOTE if you are getting here while running in IJ then you have probable not run
                    // local.yaml.sh
                    LOGGER.warn("YAML config file [{}] from arguments [{}] is not a valid file.\n" +
                                "You need to supply a valid stroom configuration YAML file.",
                            yamlFile, Arrays.asList(args));
                }
            }
        }

        if (path == null) {
            throw new RuntimeException(
                    "Could not extract YAML config file from arguments [" + Arrays.asList(args) + "]");
        }

        Path realConfigFile = null;
        try {
            realConfigFile = path.toRealPath();
            LOGGER.info("Using config file: \"" + realConfigFile + "\"");
        } catch (final IOException e) {
            LOGGER.error("Unable to find location of real config file from \"" + path + "\"");
        }

        return realConfigFile;
    }

}
