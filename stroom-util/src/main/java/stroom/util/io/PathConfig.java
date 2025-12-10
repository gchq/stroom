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

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

// Can be injected either as PathConfig or one of its subclasses
@JsonPropertyOrder(alphabetic = true)
public abstract class PathConfig extends AbstractConfig implements IsProxyConfig, IsStroomConfig {

    public static final String PROP_NAME_HOME = "home";
    public static final String PROP_NAME_TEMP = "temp";

    @JsonProperty(PROP_NAME_HOME)
    private final String home;

    @JsonProperty(PROP_NAME_TEMP)
    private final String temp;

    public PathConfig() {
        home = null;
        temp = null;
    }

    @JsonCreator
    public PathConfig(@JsonProperty(PROP_NAME_HOME) final String home,
                      @JsonProperty(PROP_NAME_TEMP) final String temp) {
        this.home = home;
        this.temp = temp;
    }

    public String getHome() {
        return home;
    }

    public String getTemp() {
        return temp;
    }

    @Override
    public String toString() {
        return "PathConfig{" +
               "home='" + home + '\'' +
               ", temp='" + temp + '\'' +
               '}';
    }
}
