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

import java.net.URI;

public interface UriFactory {

    /**
     * Creates a URI that can be used to connect to this application directly from another node or from this one.
     * It should not return localhost in a multi node environment but instead provide an FQDN.
     * The URI is for direct connection to the application and not via a proxy.
     *
     * @param path The path of the URI.
     * @return A URI to connect directly to the application.
     */
    URI nodeUri(String path);

    /**
     * If the application is served by a proxy, e.g. NGINX, then the public facing URI will be different from the
     * nodeUri. In this case the public URI needs to be configured.
     * <p>
     * If not configured this will return the same thing as nodeUri.
     *
     * @param path The path of the URI.
     * @return A URI to connect to the application from beyond a proxy.
     */
    URI publicUri(String path);

    /**
     * If the UI is being served separately from the rest of the application, e.g. when developing React code
     * externally, then this URI must point to the URL serving the UI.
     * <p>
     * If not configured this will return the same thing as publicUri.
     *
     * @param path The path of the URI.
     * @return A URI for the UI.
     */
    URI uiUri(String path);
}
