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

package stroom.db.util;

import java.util.Objects;

public final class DbUrl {

    private final String scheme;
    private final String host;
    private final int port;
    private final String dbName;
    private final String query;

    private DbUrl(final String scheme,
                  final String host,
                  final int port,
                  final String dbName,
                  final String query) {
        this.scheme = scheme;
        this.host = host;
        this.port = port;
        this.dbName = dbName;
        this.query = query;
    }

    public static DbUrl parse(final String url) {
        return new Builder().parse(url).build();
    }

    public String getScheme() {
        return scheme;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDbName() {
        return dbName;
    }

    public String getQuery() {
        return query;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DbUrl dbUrl = (DbUrl) o;
        return Objects.equals(scheme, dbUrl.scheme) &&
                Objects.equals(host, dbUrl.host) &&
                Objects.equals(port, dbUrl.port) &&
                Objects.equals(dbName, dbUrl.dbName) &&
                Objects.equals(query, dbUrl.query);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheme, host, port, dbName, query);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        if (scheme != null && !scheme.isEmpty()) {
            sb.append(scheme);
            sb.append("://");
        }
        if (host != null && !host.isEmpty()) {
            sb.append(host);
        }
        if (port > 0) {
            sb.append(":");
            sb.append(port);
        }
        if (dbName != null && !dbName.isEmpty()) {
            sb.append("/");
            sb.append(dbName);
        }
        if (query != null && !query.isEmpty()) {
            sb.append("?");
            sb.append(query);
        }
        return sb.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private String scheme = "jdbc:mysql";
        private String host = "localhost";
        private int port = 3306;
        private String dbName;
        private String query;

        private Builder() {
        }

        private Builder(final DbUrl dbUrl) {
            this.scheme = dbUrl.scheme;
            this.host = dbUrl.host;
            this.port = dbUrl.port;
            this.dbName = dbUrl.dbName;
            this.query = dbUrl.query;
        }

        public Builder parse(final String url) {
            String remaining = url;

            int index = remaining.indexOf("://");
            if (index != -1) {
                scheme(remaining.substring(0, index));
                remaining = remaining.substring(index + 3);
            }

            String hostAndPort = remaining;

            index = remaining.indexOf("/");
            if (index != -1) {
                // We have a path
                hostAndPort = remaining.substring(0, index);
                remaining = remaining.substring(index + 1);

                index = remaining.indexOf("?");
                if (index != -1) {
                    dbName(remaining.substring(0, index));
                } else {
                    dbName(remaining);
                }

            } else {
                // There is no path.
                index = remaining.indexOf("?");
                if (index != -1) {
                    hostAndPort = remaining.substring(0, index);
                }
            }

            // Get host and port.
            index = hostAndPort.indexOf(":");
            if (index != -1) {
                host(hostAndPort.substring(0, index));
                port(Integer.parseInt(hostAndPort.substring(index + 1)));
            } else {
                host(hostAndPort);
            }

            // Get the query.
            index = remaining.indexOf("?");
            if (index != -1) {
                query(remaining.substring(index + 1));
            }

            return this;
        }

        public Builder scheme(final String scheme) {
            this.scheme = scheme;
            return this;
        }

        public Builder host(final String host) {
            this.host = host;
            return this;
        }

        public Builder port(final int port) {
            this.port = port;
            return this;
        }

        public Builder dbName(final String dbName) {
            this.dbName = dbName;
            return this;
        }

        public Builder query(final String query) {
            this.query = query;
            return this;
        }

        public DbUrl build() {
            return new DbUrl(scheme, host, port, dbName, query);
        }
    }
}
