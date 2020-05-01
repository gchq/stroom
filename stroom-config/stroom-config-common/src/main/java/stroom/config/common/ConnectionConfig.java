/*
 * Copyright 2018 Crown Copyright
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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.config.annotations.ReadOnly;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.NotInjectableConfig;

import java.util.Objects;

@NotInjectableConfig
public class ConnectionConfig extends AbstractConfig {
    public static final String PROP_NAME_JDBC_DRIVER_CLASS_NAME = "jdbcDriverClassName";
    public static final String PROP_NAME_JDBC_DRIVER_URL = "jdbcDriverUrl";
    public static final String PROP_NAME_JDBC_DRIVER_USERNAME = "jdbcDriverUsername";
    public static final String PROP_NAME_JDBC_DRIVER_PASSWORD = "jdbcDriverPassword";
    private static final String COMMON_DESCRIPTION = "Should only be set in the application YAML config file. " +
        "Connection details can be set in one place using 'stroom.commonDbDetails.*', individually for each " +
        "service area or a mixture of the two.";

    private String jdbcDriverClassName;
    private String jdbcDriverUrl;
    private String jdbcDriverUsername;
    private String jdbcDriverPassword;

    @ReadOnly
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("The class name for the JDBC database connection, e.g. com.mysql.cj.jdbc.Driver. "
        + COMMON_DESCRIPTION)
    @JsonProperty(PROP_NAME_JDBC_DRIVER_CLASS_NAME)
    public String getJdbcDriverClassName() {
        return jdbcDriverClassName;
    }

    public void setJdbcDriverClassName(final String jdbcDriverClassName) {
        this.jdbcDriverClassName = jdbcDriverClassName;
    }

    @ReadOnly
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("The URL for the JDBC database connection, e.g. " +
        "jdbc:mysql://some-host:3306/db-name?useUnicode=yes&characterEncoding=UTF-8. "
        + COMMON_DESCRIPTION)
    @JsonProperty(PROP_NAME_JDBC_DRIVER_URL)
    public String getJdbcDriverUrl() {
        return jdbcDriverUrl;
    }

    public void setJdbcDriverUrl(final String jdbcDriverUrl) {
        this.jdbcDriverUrl = jdbcDriverUrl;
    }

    @ReadOnly
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("The username to connect to the database with. "
        + COMMON_DESCRIPTION)
    @JsonProperty(PROP_NAME_JDBC_DRIVER_USERNAME)
    public String getJdbcDriverUsername() {
        return jdbcDriverUsername;
    }

    public void setJdbcDriverUsername(final String jdbcDriverUsername) {
        this.jdbcDriverUsername = jdbcDriverUsername;
    }

    @ReadOnly
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("The password to connect to the database with. "
        + COMMON_DESCRIPTION)
    @JsonProperty(PROP_NAME_JDBC_DRIVER_PASSWORD)
    public String getJdbcDriverPassword() {
        return jdbcDriverPassword;
    }

    public void setJdbcDriverPassword(final String jdbcDriverPassword) {
        this.jdbcDriverPassword = jdbcDriverPassword;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ConnectionConfig that = (ConnectionConfig) o;
        return Objects.equals(jdbcDriverClassName, that.jdbcDriverClassName) &&
                Objects.equals(jdbcDriverUrl, that.jdbcDriverUrl) &&
                Objects.equals(jdbcDriverUsername, that.jdbcDriverUsername) &&
                Objects.equals(jdbcDriverPassword, that.jdbcDriverPassword);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jdbcDriverClassName, jdbcDriverUrl, jdbcDriverUsername, jdbcDriverPassword);
    }

    @Override
    public String toString() {
        return "ConnectionConfig{" +
                "jdbcDriverClassName='" + jdbcDriverClassName + '\'' +
                ", jdbcDriverUrl='" + jdbcDriverUrl + '\'' +
                ", jdbcDriverUsername='" + jdbcDriverUsername + '\'' +
                ", jdbcDriverPassword='" + jdbcDriverPassword + '\'' +
                '}';
    }

    public static class Builder {
        private final ConnectionConfig instance;

        public Builder() {
            this(new ConnectionConfig());
        }

        public Builder(ConnectionConfig instance) {
            this.instance = instance;
        }

        public Builder jdbcDriverClassName(final String value) {
            this.instance.setJdbcDriverClassName(value);
            return this;
        }

        public Builder jdbcUrl(final String value) {
            this.instance.setJdbcDriverUrl(value);
            return this;
        }

        public Builder username(final String value) {
            this.instance.setJdbcDriverUsername(value);
            return this;
        }

        public Builder password(final String value) {
            this.instance.setJdbcDriverPassword(value);
            return this;
        }

        public ConnectionConfig build() {
            return instance;
        }
    }
}
