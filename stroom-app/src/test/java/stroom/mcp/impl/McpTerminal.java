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

package stroom.mcp.impl;

import stroom.mcp.impl.tools.SampleUploads;
import stroom.security.api.SecurityContext;
import stroom.util.json.JsonUtil;
import stroom.util.shared.UserRef;

import com.google.inject.Injector;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The user's terminal, running a command `upload_sample files=` gave: the file goes, as the user, to the REAL
/// upload servlet (`/mcp/upload/<id>?file=<path>`) the command names. Only the HTTP transport is left out.
public final class McpTerminal {

    private static final Pattern UPLOAD_ID = Pattern.compile(Pattern.quote(SampleUploads.PATH) + "/([^?'\"]+)\\?file=");

    private McpTerminal() {
    }

    /// Runs a command, posting the file's content to the upload it names.
    ///
    /// @param injector The test's injector, holding the servlet.
    /// @param user     The user running it, signed in.
    /// @param command  The command, as upload_sample gave it.
    /// @param file     The file's path, as the command names it.
    /// @param content  The file's content.
    /// @return What the servlet answered.
    public static Posted run(final Injector injector,
                             final UserRef user,
                             final String command,
                             final String file,
                             final byte[] content) {
        final Matcher matcher = UPLOAD_ID.matcher(command);
        if (!matcher.find()) {
            throw new AssertionError("No upload in the command: " + command);
        }
        final McpUploadServlet servlet = injector.getInstance(McpUploadServlet.class);
        final SecurityContext securityContext = injector.getInstance(SecurityContext.class);
        final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        final HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        final StringWriter body = new StringWriter();
        final int[] status = new int[]{HttpServletResponse.SC_OK};
        try {
            Mockito.when(request.getMethod()).thenReturn("POST");
            Mockito.when(request.getPathInfo()).thenReturn("/" + matcher.group(1));
            Mockito.when(request.getQueryString()).thenReturn(McpUploadServlet.FILE_PARAMETER + "="
                                                              + URLEncoder.encode(file, StandardCharsets.UTF_8));
            Mockito.when(request.getContentType()).thenReturn("application/octet-stream");
            Mockito.when(request.getInputStream()).thenReturn(new BodyStream(content));
            Mockito.when(response.getWriter()).thenReturn(new PrintWriter(body, true));
            Mockito.doAnswer(invocation -> {
                status[0] = invocation.getArgument(0);
                return null;
            }).when(response).setStatus(Mockito.anyInt());
            securityContext.asUser(user, () -> {
                try {
                    servlet.service(request, response);
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        @SuppressWarnings("unchecked") final Map<String, Object> json = JsonUtil.readValue(body.toString(),
                Map.class);
        return new Posted(status[0], json);
    }


    // --------------------------------------------------------------------------------


    /// What the upload servlet answered a post with.
    ///
    /// @param status The HTTP status.
    /// @param body   The JSON body.
    public record Posted(int status, Map<String, Object> body) {

    }

    // A post's body, as the servlet container gives it.
    private static final class BodyStream extends ServletInputStream {

        private final ByteArrayInputStream body;

        private BodyStream(final byte[] body) {
            this.body = new ByteArrayInputStream(body);
        }

        @Override
        public boolean isFinished() {
            return body.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(final ReadListener readListener) {
            throw new UnsupportedOperationException("Not read asynchronously");
        }

        @Override
        public int read() {
            return body.read();
        }

        @Override
        public int read(final byte[] bytes, final int offset, final int length) {
            return body.read(bytes, offset, length);
        }
    }
}
