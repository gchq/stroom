/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.util.http;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.net.SsrfGuard;

import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.protocol.HttpContext;

import java.net.URI;

/**
 * Applies the configured redirect policy to a client built from {@link HttpClientConfiguration}.
 * <p>
 * When redirects are turned off the redirect response is handed back to the caller as it stands, so the
 * caller can see and report the 3xx rather than silently getting nothing. When they are on, each hop is
 * re-checked with {@link SsrfGuard}, since the check made before the request only covers the address that
 * was asked for, not wherever the endpoint chooses to send us next.
 * </p>
 */
public class ConfiguredRedirectStrategy extends DefaultRedirectStrategy {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ConfiguredRedirectStrategy.class);

    private final boolean followRedirects;

    public ConfiguredRedirectStrategy(final boolean followRedirects) {
        this.followRedirects = followRedirects;
    }

    @Override
    public boolean isRedirected(final HttpRequest request,
                                final HttpResponse response,
                                final HttpContext context) throws ProtocolException {
        if (!followRedirects) {
            LOGGER.debug(() -> "isRedirected() - not following the redirect to '"
                               + response.getFirstHeader("Location")
                               + "' as redirects are disabled for this client");
            return false;
        }
        return super.isRedirected(request, response, context);
    }

    @Override
    public URI getLocationURI(final HttpRequest request,
                              final HttpResponse response,
                              final HttpContext context) throws HttpException {
        final URI uri = super.getLocationURI(request, response, context);
        // Re-check every hop, as a redirect could otherwise reach an address that would have been refused
        // had it been asked for directly.
        try {
            SsrfGuard.rejectMetadataAndWildcard(uri.toString());
        } catch (final IllegalArgumentException e) {
            throw new ProtocolException(e.getMessage(), e);
        }
        return uri;
    }
}
