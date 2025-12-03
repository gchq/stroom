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

package stroom.util.io;

import stroom.util.config.annotations.ReadOnly;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.shared.BootStrapConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.inject.Singleton;

@BootStrapConfig
@Singleton
@JsonPropertyOrder(alphabetic = true)
public class StroomPathConfig extends PathConfig implements IsStroomConfig {

    public StroomPathConfig() {
        super();
    }

    @JsonCreator
    public StroomPathConfig(@JsonProperty("home") final String home,
                            @JsonProperty("temp") final String temp) {
        super(home, temp);
    }

//    private static final String DEFAULT_HOME_DIR = ".";
//    private static final String DEFAULT_TEMP_DIR = "/tmp/stroom";
//
//    public StroomPathConfig() {
//        super(DEFAULT_HOME_DIR, DEFAULT_TEMP_DIR);
//    }

    /**
     * Will be created on boot in App
     */
    @Override
    @ReadOnly
    @RequiresRestart(RestartScope.SYSTEM)
    @JsonPropertyDescription("By default, unless configured otherwise, all other configured paths " +
            "(except stroom.path.temp) will be relative to this directory. If this value is null then" +
            "Stroom will use either of the following to derive stroom.path.home: the directory of the Stroom " +
            "application JAR file or ~/.stroom. Should only be set per node in application YAML configuration file. " +
            "It must be an absolute path and it does not support '~' or variable substitution like other paths.")
    public String getHome() {
        return super.getHome();
    }

    /**
     * Will be created on boot in App
     */
    @Override
    @ReadOnly
    @RequiresRestart(RestartScope.SYSTEM)
    @JsonPropertyDescription("This directory is used by stroom to write any temporary file to. " +
            "Should only be set per node in application YAML configuration file. " +
            "If not set then Stroom will use <SYSTEM TEMP>/stroom." +
            "It must be an absolute path and it does not support '~' or variable substitution like other paths.")
    public String getTemp() {
        return super.getTemp();
    }

    public StroomPathConfig withHome(final String home) {
        return new StroomPathConfig(home, getTemp());
    }

    public StroomPathConfig withTemp(final String temp) {
        return new StroomPathConfig(getHome(), temp);
    }
}
