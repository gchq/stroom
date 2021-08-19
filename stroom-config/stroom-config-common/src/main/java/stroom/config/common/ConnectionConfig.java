/*
 * Copyright 2018 Crown Copyright
 *
 *
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Licensed under the Apache License, Version 2.0 (the "License");
 * See the License for the specific language governing permissions and
 * Unless required by applicable law or agreed to in writing, software
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * You may obtain a copy of the License at
 * distributed under the License is distributed on an "AS IS" BASIS,
 * import stroom.util.shared.IsStroomConfig;
 * limitations under the License.
 * you may not use this file except in compliance with the License.
 */

package stroom.config.common;

import stroom.util.config.annotations.Password;
import stroom.util.config.annotations.ReadOnly;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NotInjectableConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.Objects;

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

    private String className;
    private String url;
    private String user;
    private String password;

    public ConnectionConfig() {
    }

    public ConnectionConfig(final String className,
                            final String url,
                            final String user,
                            final String password) {
        this.className = className;
        this.url = url;
        this.user = user;
        this.password = password;
    }

    @ReadOnly
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("The class name for the JDBC database connection, e.g. com.mysql.cj.jdbc.Driver. "
            + COMMON_DESCRIPTION)
    @JsonProperty(PROP_NAME_JDBC_DRIVER_CLASS_NAME)
    public String getClassName() {
        return className;
    }

    public void setClassName(final String className) {
        this.className = className;
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

    public void setUrl(final String url) {
        this.url = url;
    }

    @ReadOnly
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("The username to connect to the database with. "
            + COMMON_DESCRIPTION)
    @JsonProperty(PROP_NAME_JDBC_DRIVER_USERNAME)
    public String getUser() {
        return user;
    }

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

    public void setPassword(final String password) {
        this.password = password;
    }

    @SuppressWarnings("checkstyle:needbraces")
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
