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

import stroom.ai.impl.AiConfig;
import stroom.ai.impl.McpEndpointConfig;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.mcp.impl.tools.FeedUploads;
import stroom.mcp.impl.tools.SampleUploads;
import stroom.meta.api.StandardHeaderArguments;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.IsServlet;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/// `POST /mcp/upload/<id>?file=<name>`: a sample file from the user's machine, posted by a command upload_sample
/// gave (ported from the Stroom MCP server's `utils/uploads.py` `handle_upload`), stored in the feed the command was
/// given for, as the user ([FeedUploads]).
///
/// The request is signed in as any other is (the security filter: the user's API key or token, as the command's
/// `Authorization` header gives it), so it needs no upload ticket: the post must match a file upload_sample gave
/// this user a command for, which hasn't run out or been posted already ([SampleUploads]). As at `/mcp`, the user
/// needs the Use MCP Tools permission, and nothing is served while the MCP endpoint is turned off.
///
/// The file's name is read from the query string itself, never as a request parameter: curl's `--data-binary`
/// sends the body as a form (`application/x-www-form-urlencoded`) unless told otherwise, and reading a parameter
/// would have the servlet container parse the file as one (and refuse one over its form size limit).
public class McpUploadServlet extends HttpServlet implements IsServlet {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(McpUploadServlet.class);

    /// The file's name, as the command gives it.
    static final String FILE_PARAMETER = "file";
    /// The most characters of a client-supplied value written to the log.
    static final int MAX_LOGGED_CHARS = 300;

    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");

    private final Provider<SampleUploads> sampleUploadsProvider;
    private final Provider<FeedUploads> feedUploadsProvider;
    private final SecurityContext securityContext;
    private final Provider<AiConfig> aiConfigProvider;

    @Inject
    McpUploadServlet(final Provider<SampleUploads> sampleUploadsProvider,
                     final Provider<FeedUploads> feedUploadsProvider,
                     final SecurityContext securityContext,
                     final Provider<AiConfig> aiConfigProvider) {
        this.sampleUploadsProvider = sampleUploadsProvider;
        this.feedUploadsProvider = feedUploadsProvider;
        this.securityContext = securityContext;
        this.aiConfigProvider = aiConfigProvider;
    }

    /// @return Where the files are posted.
    @Override
    public Set<String> getPathSpecs() {
        return Set.of(SampleUploads.PATH + "/*");
    }

    @Override
    protected void service(final HttpServletRequest request, final HttpServletResponse response)
            throws IOException {
        final McpEndpointConfig config = NullSafe.getOrElse(aiConfigProvider.get(), AiConfig::getMcpEndpoint,
                new McpEndpointConfig());
        if (!config.isEnabled()) {
            error(response, HttpServletResponse.SC_NOT_FOUND, "Stroom's MCP endpoint is turned off.");
        } else if (!securityContext.hasAppPermission(AppPermission.USE_MCP_TOOLS)) {
            error(response, HttpServletResponse.SC_FORBIDDEN, "Using Stroom's tools over MCP needs the '"
                                                              + AppPermission.USE_MCP_TOOLS.getDisplayValue()
                                                              + "' permission.");
        } else if (!"POST".equalsIgnoreCase(request.getMethod())) {
            error(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST the file: curl --data-binary "
                                                                       + "@<file>, as the command gives it.");
        } else {
            upload(request, response);
        }
    }

    private void upload(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        final String id = NullSafe.string(request.getPathInfo()).replace("/", "");
        final String file = queryParameter(request, FILE_PARAMETER);
        final String user = NullSafe.get(securityContext.getUserRef(), UserRef::getUuid);
        final SampleUploads uploads = sampleUploadsProvider.get();
        final Optional<SampleUploads.Pending> claimed = user == null
                ? Optional.empty()
                : uploads.claim(id, user, file);
        if (claimed.isEmpty()) {
            LOGGER.info(() -> LogUtil.message("Refused an upload of '{}' by {}: no upload waits for it",
                    forLog(file), securityContext.getUserIdentityForAudit()));
            error(response, HttpServletResponse.SC_FORBIDDEN, "No upload waits for this file: the command was not "
                                                              + "copied exactly as the agent was given it, it ran "
                                                              + "out (the commands last minutes), the file was sent "
                                                              + "already, or a server restart forgot it. Ask the "
                                                              + "agent for fresh commands (upload_sample with "
                                                              + "files=) and run them as given.");
            return;
        }
        final SampleUploads.Pending pending = claimed.get();
        final long limit = uploads.maxBytes();
        final Map<String, String> receipt = new LinkedHashMap<>();
        receipt.put(StandardHeaderArguments.TYPE, pending.streamType());
        receipt.putAll(pending.headers());
        // Released unless the file is stored, whatever goes wrong, so it can be posted again.
        boolean stored = false;
        try (final InputStream data = request.getInputStream()) {
            final FeedUploads.Sent sent = feedUploadsProvider.get().send(pending.feed(), pending.streamType(),
                    receipt, file, data, limit);
            stored = true;
            LOGGER.info(() -> LogUtil.message("{} uploaded '{}' to feed {}: stream {}, {} bytes",
                    securityContext.getUserIdentityForAudit(), forLog(file), pending.feed(), sent.streamId(),
                    sent.bytes()));
            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("feed", pending.feed());
            result.put("file", file);
            result.putAll(sent.toMap());
            write(response, HttpServletResponse.SC_OK, result);
        } catch (final FeedUploads.TooLarge e) {
            error(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, e.getMessage());
        } catch (final FeedUploads.NoData e) {
            error(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (final ToolRefusal e) {
            LOGGER.info(() -> LogUtil.message("Upload of '{}' to feed {} failed: {}", forLog(file), pending.feed(),
                    e.getMessage()));
            error(response, HttpServletResponse.SC_BAD_GATEWAY, e.getMessage());
        } catch (final RuntimeException e) {
            LOGGER.error(() -> LogUtil.message("Upload of '{}' to feed {} failed", forLog(file), pending.feed()), e);
            error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Stroom couldn't store the file: "
                                                                          + NullSafe.nonBlankStringElse(
                                                                                  e.getMessage(),
                                                                                  e.getClass().getSimpleName())
                                                                          + ". Run the command again.");
        } finally {
            if (!stored) {
                uploads.release(pending, file);
            }
        }
    }

    /// @param request A request.
    /// @param name    A parameter's name.
    /// @return The parameter's first value in the request's query string (decoded as UTF-8), or "" if it has
    /// none. The body is never read for it.
    static String queryParameter(final HttpServletRequest request, final String name) {
        for (final String pair : NullSafe.string(request.getQueryString()).split("&")) {
            final int equals = pair.indexOf('=');
            final String key = equals < 0
                    ? pair
                    : pair.substring(0, equals);
            if (decode(key).equals(name)) {
                return equals < 0
                        ? ""
                        : decode(pair.substring(equals + 1));
            }
        }
        return "";
    }

    private static String decode(final String text) {
        try {
            return URLDecoder.decode(text, StandardCharsets.UTF_8);
        } catch (final IllegalArgumentException e) {
            // A malformed escape: taken as it is, so it matches no upload.
            return text;
        }
    }

    /// @param value A value the client gave.
    /// @return It, as it may be written to the log: control characters (e.g. line breaks, which could forge log
    /// lines) as `?`, and cut at [#MAX_LOGGED_CHARS].
    static String forLog(final String value) {
        final String text = CONTROL_CHARACTERS.matcher(NullSafe.string(value)).replaceAll("?");
        return text.length() > MAX_LOGGED_CHARS
                ? text.substring(0, MAX_LOGGED_CHARS) + "…"
                : text;
    }

    private static void error(final HttpServletResponse response, final int status, final String message)
            throws IOException {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        write(response, status, body);
    }

    private static void write(final HttpServletResponse response, final int status, final Map<String, Object> body)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(JsonUtil.writeValueAsString(body, false));
        response.getWriter().flush();
    }
}
