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

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class CrossModuleConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    public static final String NAME = "crossModule";
    public static final String PROP_NAME_DB = "db";

    private final CrossModuleDbConfig crossModuleDbConfig;

    public CrossModuleConfig() {
        this.crossModuleDbConfig = new CrossModuleDbConfig();
    }

    @JsonCreator
    public CrossModuleConfig(@JsonProperty(PROP_NAME_DB) final CrossModuleDbConfig crossModuleDbConfig) {
        this.crossModuleDbConfig = crossModuleDbConfig;
    }

    @JsonProperty(PROP_NAME_DB)
    public CrossModuleDbConfig getDbConfig() {
        return crossModuleDbConfig;
    }


    // --------------------------------------------------------------------------------


    @BootStrapConfig
    public static class CrossModuleDbConfig extends AbstractDbConfig implements IsStroomConfig {

        public CrossModuleDbConfig() {
            super();
        }

        @SuppressWarnings("unused")
        @JsonCreator
        public CrossModuleDbConfig(
                @JsonProperty(PROP_NAME_CONNECTION) final ConnectionConfig connectionConfig,
                @JsonProperty(PROP_NAME_CONNECTION_POOL) final ConnectionPoolConfig connectionPoolConfig) {
            super(connectionConfig, connectionPoolConfig);
        }
    }
}
