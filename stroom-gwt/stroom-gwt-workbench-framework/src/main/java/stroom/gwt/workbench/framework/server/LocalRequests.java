/*
 * Copyright 2026 Crown Copyright
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

package stroom.gwt.workbench.framework.server;

import com.sun.net.httpserver.HttpExchange;

import java.util.Locale;
import java.util.Set;

/// Checks that requests come from the workbench's own pages on the local machine.
///
/// The `Host` header is checked to stop DNS rebinding, where another site's name is pointed
/// at the loopback address so its pages count as the same origin as the workbench.
final class LocalRequests {

    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    private LocalRequests() {
        // Static utility
    }

    /// @param exchange A request.
    /// @return True if the request's `Host` header names the loopback interface and the port
    /// the request was received on.
    static boolean isAllowedHost(final HttpExchange exchange) {
        final String host = exchange.getRequestHeaders().getFirst("Host");
        return host != null && isLoopbackAuthority(host, exchange.getLocalAddress().getPort());
    }

    /// @param origin The `Origin` header of a request, may be null.
    /// @param port   The port the server listens on.
    /// @return True if the origin is the workbench's own, i.e. `http://<loopback host>:<port>`.
    static boolean isAllowedOrigin(final String origin, final int port) {
        final String prefix = "http://";
        return origin != null
               && origin.toLowerCase(Locale.ROOT).startsWith(prefix)
               && isLoopbackAuthority(origin.substring(prefix.length()), port);
    }

    private static boolean isLoopbackAuthority(final String authority, final int port) {
        final String suffix = ":" + port;
        final String lowerCase = authority.toLowerCase(Locale.ROOT);
        return lowerCase.endsWith(suffix)
               && LOOPBACK_HOSTS.contains(lowerCase.substring(0, lowerCase.length() - suffix.length()));
    }
}
