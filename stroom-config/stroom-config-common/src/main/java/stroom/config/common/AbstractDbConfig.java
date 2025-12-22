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

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NotInjectableConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@BootStrapConfig
@NotInjectableConfig
public abstract class AbstractDbConfig extends AbstractConfig implements IsStroomConfig {

    public static final String PROP_NAME_CONNECTION = "connection";
    public static final String PROP_NAME_CONNECTION_POOL = "connectionPool";

    private ConnectionConfig connectionConfig;
    private ConnectionPoolConfig connectionPoolConfig;

    public AbstractDbConfig() {
        connectionConfig = new ConnectionConfig();
        connectionPoolConfig = new ConnectionPoolConfig();
    }

    @JsonCreator
    public AbstractDbConfig(@JsonProperty(PROP_NAME_CONNECTION) final ConnectionConfig connectionConfig,
                            @JsonProperty(PROP_NAME_CONNECTION_POOL) final ConnectionPoolConfig connectionPoolConfig) {
        this.connectionConfig = connectionConfig;
        this.connectionPoolConfig = connectionPoolConfig;
    }

    @JsonProperty(PROP_NAME_CONNECTION)
    public ConnectionConfig getConnectionConfig() {
        return connectionConfig;
    }

    @SuppressWarnings("unused")
    public void setConnectionConfig(final ConnectionConfig connectionConfig) {
        this.connectionConfig = connectionConfig;
    }

    @JsonProperty(PROP_NAME_CONNECTION_POOL)
    public ConnectionPoolConfig getConnectionPoolConfig() {
        return connectionPoolConfig;
    }

    @SuppressWarnings("unused")
    public void setConnectionPoolConfig(final ConnectionPoolConfig connectionPoolConfig) {
        this.connectionPoolConfig = connectionPoolConfig;
    }

    @Override
    public String toString() {
        return "DbConfig{" +
                "connectionConfig=" + connectionConfig +
                ", connectionPoolConfig=" + connectionPoolConfig +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractDbConfig dbConfig = (AbstractDbConfig) o;
        return connectionConfig.equals(dbConfig.connectionConfig) &&
                connectionPoolConfig.equals(dbConfig.connectionPoolConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionConfig, connectionPoolConfig);
    }
}
