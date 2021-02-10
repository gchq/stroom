/*
 * Copyright 2020 Crown Copyright
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
package stroom.event.logging.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;

import stroom.util.shared.AbstractConfig;


@Singleton
public class LoggingConfig extends AbstractConfig {
    private boolean logEveryRestCallEnabled = false;

    @JsonProperty("logEveryRestCallEnabled")
    @JsonPropertyDescription("Ensure that every RESTful service calls is logged, not only user initiated ones.")
    public boolean isLogEveryRestCallEnabled() {
        return logEveryRestCallEnabled;
    }

    public void setLogEveryRestCallEnabled(final boolean logEveryRestCallEnabled) {
        this.logEveryRestCallEnabled = logEveryRestCallEnabled;
    }


    @Override
    public String toString() {
        return "LoggingConfig{" +
                "logEveryRestCallEnabled=" + logEveryRestCallEnabled +
                '}';
    }

}
