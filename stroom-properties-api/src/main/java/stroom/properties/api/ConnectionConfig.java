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

package stroom.properties.api;

import java.util.Objects;

public class ConnectionConfig {
    private static final String PROP_JDBC_CLASS_NAME = "jdbcDriverClassName";
    private static final String PROP_JDBC_DRIVER_URL = "jdbcDriverUrl";
    private static final String PROP_JDBC_DRIVER_USERNAME = "jdbcDriverUsername";
    private static final String PROP_JDBC_DRIVER_PASSWORD = "jdbcDriverPassword";

    private final String jdbcDriverClassName;
    private final String jdbcDriverUrl;
    private final String jdbcDriverUsername;
    private final String jdbcDriverPassword;

    public ConnectionConfig(final String prefix, final StroomPropertyService stroomPropertyService) {
        this.jdbcDriverClassName = stroomPropertyService.getProperty(prefix + PROP_JDBC_CLASS_NAME);
        this.jdbcDriverUrl = stroomPropertyService.getProperty(prefix + PROP_JDBC_DRIVER_URL + "|trace");
        this.jdbcDriverUsername = stroomPropertyService.getProperty(prefix + PROP_JDBC_DRIVER_USERNAME);
        this.jdbcDriverPassword = stroomPropertyService.getProperty(prefix + PROP_JDBC_DRIVER_PASSWORD);
    }

    public String getJdbcDriverClassName() {
        return jdbcDriverClassName;
    }

    public String getJdbcDriverUrl() {
        return jdbcDriverUrl;
    }

    public String getJdbcDriverUsername() {
        return jdbcDriverUsername;
    }

    public String getJdbcDriverPassword() {
        return jdbcDriverPassword;
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
}
