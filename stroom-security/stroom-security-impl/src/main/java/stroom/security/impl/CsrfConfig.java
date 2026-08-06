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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Cross-Site Request Forgery protection settings applied by {@link SecurityFilter}.
 */
@JsonPropertyOrder(alphabetic = true)
public class CsrfConfig extends AbstractConfig implements IsStroomConfig {

    public static final String PROP_NAME_PROTECT_BROWSER_ORIGINATED_REQUESTS =
            "protectBrowserOriginatedRequests";

    private static final boolean DEFAULT_PROTECT_BROWSER_ORIGINATED_REQUESTS = true;

    private final boolean protectBrowserOriginatedRequests;

    public CsrfConfig() {
        protectBrowserOriginatedRequests = DEFAULT_PROTECT_BROWSER_ORIGINATED_REQUESTS;
    }

    @JsonCreator
    public CsrfConfig(
            @JsonProperty(PROP_NAME_PROTECT_BROWSER_ORIGINATED_REQUESTS)
            final Boolean protectBrowserOriginatedRequests) {
        this.protectBrowserOriginatedRequests = Objects.requireNonNullElse(
                protectBrowserOriginatedRequests, DEFAULT_PROTECT_BROWSER_ORIGINATED_REQUESTS);
    }

    @JsonProperty(PROP_NAME_PROTECT_BROWSER_ORIGINATED_REQUESTS)
    @JsonPropertyDescription(
            "Defence in depth against an undeclared authenticating proxy: reject a state-changing " +
            "request that was authenticated by a request token, whose fetch metadata " +
            "(Sec-Fetch-Site) says the browser made it cross-site, and that carries no X-CSRF " +
            "header. Cross-origin scripts cannot attach an Authorization header and forms cannot " +
            "at all, so such a token can only have been injected by an intermediary - i.e. the " +
            "credential is ambient even though edgeAuthentication.enabled has not been set. " +
            "Non-browser clients send no fetch metadata and are unaffected.")
    public boolean isProtectBrowserOriginatedRequests() {
        return protectBrowserOriginatedRequests;
    }

    @Override
    public String toString() {
        return "CsrfConfig{" +
               "protectBrowserOriginatedRequests=" + protectBrowserOriginatedRequests +
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
        final CsrfConfig that = (CsrfConfig) o;
        return protectBrowserOriginatedRequests == that.protectBrowserOriginatedRequests;
    }

    @Override
    public int hashCode() {
        return Objects.hash(protectBrowserOriginatedRequests);
    }
}
