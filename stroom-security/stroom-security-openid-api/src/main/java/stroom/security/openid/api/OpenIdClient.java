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

    public OpenIdClient(final String name,
                        final String clientId,
                        final String clientSecret) {
        this.name = Objects.requireNonNull(name);
        this.clientId = Objects.requireNonNull(clientId);
        this.clientSecret = Objects.requireNonNull(clientSecret);
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

    @Override
    public String toString() {
        // Deliberately does NOT include clientSecret, so the object can be logged without leaking it.
        return "OAuth2Client{" +
                "name='" + name + '\'' +
                ", clientId='" + clientId + '\'' +
                ", clientSecret='****'" +
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
        return Objects.equals(name, client.name)
                && Objects.equals(clientId, client.clientId)
                && Objects.equals(clientSecret, client.clientSecret);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, clientId, clientSecret);
    }
}
