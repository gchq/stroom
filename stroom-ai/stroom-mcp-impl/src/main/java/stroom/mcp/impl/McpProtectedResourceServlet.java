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

import stroom.config.common.UriFactory;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.util.json.JsonUtil;
import stroom.util.shared.IsServlet;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.Unauthenticated;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// The OAuth 2.0 protected resource metadata (RFC 9728) of Stroom's MCP endpoint: an MCP client that isn't signed
/// in is pointed here (by the `WWW-Authenticate` header of the endpoint's 401), and learns from it which identity
/// provider to sign in with.
///
/// Only an external identity provider can sign an outside client in, so with Stroom's own there is nothing here:
/// a client sends one of the user's API keys instead.
@Unauthenticated
public class McpProtectedResourceServlet extends HttpServlet implements IsServlet {

    private final Provider<OpenIdConfiguration> openIdConfigurationProvider;
    private final Provider<UriFactory> uriFactoryProvider;

    @Inject
    McpProtectedResourceServlet(final Provider<OpenIdConfiguration> openIdConfigurationProvider,
                                final Provider<UriFactory> uriFactoryProvider) {
        this.openIdConfigurationProvider = openIdConfigurationProvider;
        this.uriFactoryProvider = uriFactoryProvider;
    }

    /// @return The metadata's paths: the general one, and the one for the `/mcp` resource, which clients also try.
    @Override
    public Set<String> getPathSpecs() {
        return Set.of(
                ResourcePaths.OAUTH_PROTECTED_RESOURCE_PATH,
                ResourcePaths.OAUTH_PROTECTED_RESOURCE_PATH + ResourcePaths.MCP_PATH);
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        final Map<String, Object> metadata = metadata();
        if (metadata == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Sign in to Stroom's MCP endpoint with an API key.");
            return;
        }
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(JsonUtil.writeValueAsString(metadata, false));
    }

    /// @return The metadata, or null if there is no identity provider an outside client can sign in with.
    Map<String, Object> metadata() {
        final OpenIdConfiguration openIdConfiguration = openIdConfigurationProvider.get();
        if (openIdConfiguration == null
            || openIdConfiguration.getIdentityProviderType() != IdpType.EXTERNAL_IDP
            || openIdConfiguration.getIssuer() == null) {
            return null;
        }
        final Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("resource", uriFactoryProvider.get().publicUri(ResourcePaths.MCP_PATH).toString());
        metadata.put("authorization_servers", List.of(openIdConfiguration.getIssuer()));
        metadata.put("bearer_methods_supported", List.of("header"));
        metadata.put("resource_name", "Stroom");
        return metadata;
    }
}
