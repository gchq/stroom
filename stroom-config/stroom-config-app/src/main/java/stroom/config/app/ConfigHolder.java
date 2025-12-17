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

package stroom.config.app;

import java.nio.file.Path;

public interface ConfigHolder {

    /**
     * @return The de-serialised form of the config.yml file that has been merged with the
     * default config to ensure a full tree.
     * This does NOT include any database overrides so should only be
     * used by classes that need config values that are required in order to start the app,
     * e.g. DB connection details, or those involved in combining yaml/default/db config.
     */
    AppConfig getBootStrapConfig();

    /**
     * @return The path to the config file.
     */
    Path getConfigFile();
}
