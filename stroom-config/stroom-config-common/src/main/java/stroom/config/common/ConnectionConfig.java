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

import stroom.util.config.annotations.Password;
import stroom.util.config.annotations.ReadOnly;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NotInjectableConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@NotInjectableConfig
public class ConnectionConfig extends AbstractConfig implements IsStroomConfig {

    public static final String PROP_NAME_JDBC_DRIVER_CLASS_NAME = "jdbcDriverClassName";
    public static final String PROP_NAME_JDBC_DRIVER_URL = "jdbcDriverUrl";
    public static final String PROP_NAME_JDBC_DRIVER_USERNAME = "jdbcDriverUsername";
    public static final String PROP_NAME_JDBC_DRIVER_PASSWORD = "jdbcDriverPassword";
    private static final String COMMON_DESCRIPTION = "Should only be set in the application YAML config file. " +
            "Connection details can be set in one place using 'stroom.commonDbDetails.*', individually for each " +
            "service area or a mixture of the two.";

    // These defaults are set in CommonDbConfig. We don't set them here else the properties UI
    // will show the default conn details for each non-common db module (if not set) which is then
    // confusing for the user.
    public static final String DEFAULT_JDBC_DRIVER_CLASS_NAME = "com.mysql.cj.jdbc.Driver";
    public static final String DEFAULT_JDBC_DRIVER_URL =
            "jdbc:mysql://localhost:3307/stroom?useUnicode=yes&characterEncoding=UTF-8";
    public static final String DEFAULT_JDBC_DRIVER_USERNAME = "stroomuser";
    public static final String DEFAULT_JDBC_DRIVER_PASSWORD = "stroompassword1";

    private final String className;
    private final String url;
    // TODO 30/11/2021 AT: Make final
    private String user;
    private final String password;

    public ConnectionConfig() {
        className = null;
        url = null;
        user = null;
        password = null;
    }

    @JsonCreator
    public ConnectionConfig(@JsonProperty(PROP_NAME_JDBC_DRIVER_CLASS_NAME) final String className,
                            @JsonProperty(PROP_NAME_JDBC_DRIVER_URL) final String url,
                            @JsonProperty(PROP_NAME_JDBC_DRIVER_USERNAME) final String user,
                            @JsonProperty(PROP_NAME_JDBC_DRIVER_PASSWORD) final String password) {
        this.className = className;
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public static ConnectionConfig defaults() {
        return new ConnectionConfig(
                DEFAULT_JDBC_DRIVER_CLASS_NAME,
                DEFAULT_JDBC_DRIVER_URL,
                DEFAULT_JDBC_DRIVER_USERNAME,
                DEFAULT_JDBC_DRIVER_PASSWORD);
    }

    @ReadOnly
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("The class name for the JDBC database connection, e.g. com.mysql.cj.jdbc.Driver. "
            + COMMON_DESCRIPTION)
    @JsonProperty(PROP_NAME_JDBC_DRIVER_CLASS_NAME)
    public String getClassName() {
        return className;
    }

    @ReadOnly
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("The URL for the JDBC database connection, e.g. " +
            "jdbc:mysql://some-host:3306/db-name?useUnicode=yes&characterEncoding=UTF-8. "
            + COMMON_DESCRIPTION)
    @JsonProperty(PROP_NAME_JDBC_DRIVER_URL)
    public String getUrl() {
        return url;
    }

    @ReadOnly
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("The username to connect to the database with. "
            + COMMON_DESCRIPTION)
    @JsonProperty(PROP_NAME_JDBC_DRIVER_USERNAME)
    public String getUser() {
        return user;
    }

    @Deprecated(forRemoval = true)
    public void setUser(final String user) {
        this.user = user;
    }

    @Password
    @ReadOnly
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("The password to connect to the database with. "
            + COMMON_DESCRIPTION)
    @JsonProperty(PROP_NAME_JDBC_DRIVER_PASSWORD)
    public String getPassword() {
        return password;
    }

//    public ConnectionConfig withClassName(final String className) {
//        return new ConnectionConfig(className, url, user, password);
//    }
//
//    public ConnectionConfig withUrl(final String url) {
//        return new ConnectionConfig(className, url, user, password);
//    }
//
//    public ConnectionConfig withUser(final String user) {
//        return new ConnectionConfig(className, url, user, password);
//    }
//
//    public ConnectionConfig withPassword(final String password) {
//        return new ConnectionConfig(className, url, user, password);
//    }

    /**
     * Merges the non-null values of other into this. If other is null return this.
     */
    public ConnectionConfig merge(final ConnectionConfig other) {
        if (other == null) {
            return this;
        } else {
            return new ConnectionConfig(
                    Objects.requireNonNullElse(other.className, className),
                    Objects.requireNonNullElse(other.url, url),
                    Objects.requireNonNullElse(other.user, user),
                    Objects.requireNonNullElse(other.password, password));
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ConnectionConfig that = (ConnectionConfig) o;
        return Objects.equals(className, that.className) &&
                Objects.equals(url, that.url) &&
                Objects.equals(user, that.user) &&
                Objects.equals(password, that.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, url, user, password);
    }

    @Override
    public String toString() {
        return "ConnectionConfig{" +
                "jdbcDriverClassName='" + className + '\'' +
                ", jdbcDriverUrl='" + url + '\'' +
                ", jdbcDriverUsername='" + user + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private String className = DEFAULT_JDBC_DRIVER_CLASS_NAME;
        private String url = DEFAULT_JDBC_DRIVER_URL;
        private String user = DEFAULT_JDBC_DRIVER_USERNAME;
        private String password = DEFAULT_JDBC_DRIVER_PASSWORD;

        private Builder() {
        }

        private Builder(final ConnectionConfig connectionConfig) {
            className = connectionConfig.className;
            url = connectionConfig.url;
            user = connectionConfig.user;
            password = connectionConfig.password;
        }

        public Builder jdbcDriverClassName(final String className) {
            this.className = className;
            return this;
        }

        public Builder url(final String url) {
            this.url = url;
            return this;
        }

        public Builder user(final String user) {
            this.user = user;
            return this;
        }

        public Builder password(final String password) {
            this.password = password;
            return this;
        }

        public ConnectionConfig build() {
            return new ConnectionConfig(className, url, user, password);
        }
    }
}
