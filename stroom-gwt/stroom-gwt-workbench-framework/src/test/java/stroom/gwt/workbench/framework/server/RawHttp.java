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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/// Sends raw HTTP requests for tests, e.g. to set headers that the JDK's HTTP client won't, such
/// as `Host`.
final class RawHttp {

    private RawHttp() {
        // Static utility
    }

    /// Sends a request with no body.
    ///
    /// @param port    The local port to send it to.
    /// @param method  The method, e.g. `GET`.
    /// @param path    The path and query.
    /// @param headers Header lines, e.g. `Host: localhost:1234`.
    /// @return The status code of the response.
    /// @throws IOException If the request fails, e.g. the connection is dropped.
    static int send(final int port, final String method, final String path, final String... headers)
            throws IOException {
        final StringBuilder request = new StringBuilder()
                .append(method).append(' ').append(path).append(" HTTP/1.1\r\n");
        for (final String header : headers) {
            request.append(header).append("\r\n");
        }
        request.append("Content-Length: 0\r\nConnection: close\r\n\r\n");
        try (final Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(10_000);
            final OutputStream outputStream = socket.getOutputStream();
            outputStream.write(request.toString().getBytes(StandardCharsets.US_ASCII));
            outputStream.flush();
            final InputStream inputStream = socket.getInputStream();
            final String response = new String(inputStream.readAllBytes(), StandardCharsets.ISO_8859_1);
            if (!response.startsWith("HTTP/1.1 ")) {
                throw new IOException("No response: '" + response + "'");
            }
            return Integer.parseInt(response.substring(9, 12));
        }
    }
}
