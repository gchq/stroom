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

package stroom.security.impl;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * How stroom's logout should clear the authenticating proxy's session, when
 * {@link EdgeAuthenticationConfig#isEnabled()}.
 * <p>
 * Without this, logging out of stroom leaves the proxy's session cookie intact, so the next
 * request is silently re-authenticated. AWS documents clearing the (sharded) session cookies and
 * redirecting to the IdP's logout endpoint as the application's responsibility.
 * </p>
 */
@JsonPropertyOrder(alphabetic = true)
public class EdgeLogoutConfig extends AbstractConfig implements IsStroomConfig {

    public static final String PROP_NAME_COOKIES_TO_EXPIRE = "cookiesToExpire";
    public static final String PROP_NAME_SIGN_OUT_URL = "signOutUrl";

    private final List<String> cookiesToExpire;
    private final String signOutUrl;

    public EdgeLogoutConfig() {
        cookiesToExpire = Collections.emptyList();
        signOutUrl = null;
    }

    @JsonCreator
    public EdgeLogoutConfig(
            @JsonProperty(PROP_NAME_COOKIES_TO_EXPIRE) final List<String> cookiesToExpire,
            @JsonProperty(PROP_NAME_SIGN_OUT_URL) final String signOutUrl) {
        this.cookiesToExpire = NullSafe.list(cookiesToExpire);
        this.signOutUrl = signOutUrl;
    }

    @JsonProperty(PROP_NAME_COOKIES_TO_EXPIRE)
    @JsonPropertyDescription(
            "Cookie name prefixes to expire when the user logs out of stroom, so the " +
            "authenticating proxy's session ends too. Prefixes, because proxies shard large " +
            "session cookies, e.g. an AWS ALB's 'AWSELBAuthSessionCookie' becomes " +
            "'AWSELBAuthSessionCookie-0', '-1', ...; oauth2-proxy's '_oauth2_proxy' chunks " +
            "similarly. Every request cookie whose name starts with one of these prefixes is " +
            "expired on logout.")
    public List<String> getCookiesToExpire() {
        return cookiesToExpire;
    }

    @JsonProperty(PROP_NAME_SIGN_OUT_URL)
    @JsonPropertyDescription(
            "The authenticating proxy's or IdP's sign-out endpoint to send the browser to after " +
            "logging out of stroom, e.g. Cognito's " +
            "'https://<domain>.auth.<region>.amazoncognito.com/logout?client_id=...&logout_uri=...', " +
            "oauth2-proxy's '/oauth2/sign_out' or Cloudflare's '/cdn-cgi/access/logout'. The " +
            "post-logout landing page must be on a path the proxy does not authenticate, or the " +
            "sign-in flow will simply restart.")
    public String getSignOutUrl() {
        return signOutUrl;
    }

    @Override
    public String toString() {
        return "EdgeLogoutConfig{" +
               "cookiesToExpire=" + cookiesToExpire +
               ", signOutUrl='" + signOutUrl + '\'' +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final EdgeLogoutConfig that = (EdgeLogoutConfig) o;
        return Objects.equals(cookiesToExpire, that.cookiesToExpire)
               && Objects.equals(signOutUrl, that.signOutUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cookiesToExpire, signOutUrl);
    }
}
