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

package stroom.config.common;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.BootStrapConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

@BootStrapConfig
public class CommonDbConfig extends AbstractDbConfig {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CommonDbConfig.class);

    public CommonDbConfig() {
        // IMPORTANT - setting the defaults in this way means that they will be ignored by
        // jackson when it de-serialises the yaml. We rely on mergeConfig() being aware of these
        // defaults and factoring them in when merging config objects.
        super(new ConnectionConfig(
                        ConnectionConfig.DEFAULT_JDBC_DRIVER_CLASS_NAME,
                        ConnectionConfig.DEFAULT_JDBC_DRIVER_URL,
                        ConnectionConfig.DEFAULT_JDBC_DRIVER_USERNAME,
                        ConnectionConfig.DEFAULT_JDBC_DRIVER_PASSWORD),
                new ConnectionPoolConfig());
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public CommonDbConfig(@JsonProperty("connection") final ConnectionConfig connectionConfig,
                          @JsonProperty("connectionPool") final ConnectionPoolConfig connectionPoolConfig) {
        super(connectionConfig, connectionPoolConfig);
    }

    /**
     * Creates a new {@link AbstractDbConfig} then deep copies in the values of
     * this, followed by values of otherDbConfig.
     * Thus allowing otherDbConfig to override values in this {@link CommonDbConfig}
     */
    @JsonIgnore
    public AbstractDbConfig mergeConfig(final AbstractDbConfig otherDbConfig) {

//        // These will contain all hard coded defaults
//        final DbConfig outputConfig = new CommonDbConfig();
//        final DbConfig vanillaConfig = new CommonDbConfig();
//
//        // If the custom config has been set in common config then copy it over
//        FieldMapper.copyNonDefaults(this, outputConfig, vanillaConfig);
//        // If the custom config has been set in the module specific config then copy it over
//        FieldMapper.copyNonDefaults(otherDbConfig, outputConfig, vanillaConfig);

//        final DbConfig outputConfig = new DbConfig();

        // A new instance of ConnectionConfig has all nulls so use defaults()
        final ConnectionConfig defaultConnectionConfig = ConnectionConfig.defaults();

        final ConnectionConfig mergedConnectionConfig1 = defaultConnectionConfig.merge(getConnectionConfig());
        final ConnectionConfig mergedConnectionConfig2 = mergedConnectionConfig1.merge(
                otherDbConfig.getConnectionConfig());

        final ConnectionPoolConfig defaultPoolConfig = new ConnectionPoolConfig();

        // common trumps ctor defaults
        final ConnectionPoolConfig mergedPoolConfig1 = defaultPoolConfig.merge(
                this.getConnectionPoolConfig(), true, false);

        // merge non-null values from the module specific config over the common ones.
        final ConnectionPoolConfig mergedPoolConfig2 = mergedPoolConfig1.merge(
                otherDbConfig.getConnectionPoolConfig(),
                false,
                false);

        final AbstractDbConfig mergedConfig = new AbstractDbConfig(mergedConnectionConfig2, mergedPoolConfig2) {
        };
        LOGGER.debug("""
                        mergeConfig() called for {}
                        common: {}
                        other: {}
                        merged: {}""",
                otherDbConfig.getClass().getName(),
                this,
                otherDbConfig,
                mergedConfig);
        return mergedConfig;
    }

//    private void applyDefaultConnectionProperties(final ConnectionConfig connectionConfig) {
//        // Set some default values.
//        connectionConfig.setClassName(ConnectionConfig.DEFAULT_JDBC_DRIVER_CLASS_NAME);
//        connectionConfig.setUrl(ConnectionConfig.DEFAULT_JDBC_DRIVER_URL);
//        connectionConfig.setUser(ConnectionConfig.DEFAULT_JDBC_DRIVER_USERNAME);
//        connectionConfig.setPassword(ConnectionConfig.DEFAULT_JDBC_DRIVER_PASSWORD);
//    }

}
