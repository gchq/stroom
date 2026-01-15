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

package stroom.util.jersey;

import jakarta.inject.Singleton;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;

@Singleton
public class MockJerseyClientFactory implements JerseyClientFactory {

    private final Client client = ClientBuilder.newClient();

    @Override
    public Client getNamedClient(final JerseyClientName jerseyClientName) {
        return client;
    }

    @Override
    public Client getDefaultClient() {
        return client;
    }

    @Override
    public WebTarget createWebTarget(final JerseyClientName jerseyClientName, final String endpoint) {
        return client.target(endpoint);
    }
}
