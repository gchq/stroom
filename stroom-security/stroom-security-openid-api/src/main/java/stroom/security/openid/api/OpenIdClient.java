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

package stroom.security.openid.api;

import java.util.Objects;

public class OpenIdClient {

    private final String name;
    private final String clientId;
    private final String clientSecret;
    private final String uriPattern;

    public OpenIdClient(final String name,
                        final String clientId,
                        final String clientSecret,
                        final String uriPattern) {
        this.name = Objects.requireNonNull(name);
        this.clientId = Objects.requireNonNull(clientId);
        this.clientSecret = Objects.requireNonNull(clientSecret);
        this.uriPattern = Objects.requireNonNull(uriPattern);
    }

    public String getName() {
        return name;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getUriPattern() {
        return uriPattern;
    }

    @Override
    public String toString() {
        return "OAuth2Client{" +
                "name='" + name + '\'' +
                ", clientId='" + clientId + '\'' +
                ", clientSecret='" + clientSecret + '\'' +
                ", uriPattern='" + uriPattern + '\'' +
                '}';
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final OpenIdClient client = (OpenIdClient) object;
        return Objects.equals(name, client.name) && Objects.equals(clientId,
                client.clientId) && Objects.equals(clientSecret, client.clientSecret) && Objects.equals(
                uriPattern,
                client.uriPattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, clientId, clientSecret, uriPattern);
    }
}
