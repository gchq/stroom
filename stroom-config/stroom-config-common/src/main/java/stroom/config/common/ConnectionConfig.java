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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.config.annotations.ReadOnly;
import stroom.util.shared.IsConfig;

import java.util.Objects;

@JsonInclude(Include.NON_DEFAULT)
public class ConnectionConfig implements IsConfig {
    private String jdbcDriverClassName;
    private String jdbcDriverUrl;
    private String jdbcDriverUsername;
    private String jdbcDriverPassword;

    @ReadOnly
    @JsonPropertyDescription("Should only be set per node in application property file")
    public String getJdbcDriverClassName() {
        return jdbcDriverClassName;
    }

    public void setJdbcDriverClassName(final String jdbcDriverClassName) {
        this.jdbcDriverClassName = jdbcDriverClassName;
    }

    @ReadOnly
    @JsonPropertyDescription("Should only be set per node in application property file")
    public String getJdbcDriverUrl() {
        return jdbcDriverUrl;
    }

    public void setJdbcDriverUrl(final String jdbcDriverUrl) {
        this.jdbcDriverUrl = jdbcDriverUrl;
    }

    @ReadOnly
    @JsonPropertyDescription("Should only be set per node in application property file")
    public String getJdbcDriverUsername() {
        return jdbcDriverUsername;
    }

    public void setJdbcDriverUsername(final String jdbcDriverUsername) {
        this.jdbcDriverUsername = jdbcDriverUsername;
    }

    @ReadOnly
    @JsonPropertyDescription("Should only be set per node in application property file")
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
