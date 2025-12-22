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

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.WebTarget;

public interface JerseyClientFactory {

    /**
     * @return The client associated with the configuration for jerseyClientName or if there is
     * no configuration for that name, it will provide the client for {@link JerseyClientName#DEFAULT}.
     */
    Client getNamedClient(final JerseyClientName jerseyClientName);

    /**
     * @return The client associated with the configuration for {@link JerseyClientName#DEFAULT}.
     */
    Client getDefaultClient();

    /**
     * Helper method to create a {@link WebTarget} using the named client.
     */
    WebTarget createWebTarget(final JerseyClientName jerseyClientName,
                              final String endpoint);
}
