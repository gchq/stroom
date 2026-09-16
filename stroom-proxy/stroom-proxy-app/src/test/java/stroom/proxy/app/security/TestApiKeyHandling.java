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

package stroom.proxy.app.security;

import stroom.util.shared.UserDesc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestApiKeyHandling {

    private static final String API_KEY = "sak_0123456789_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOP";

    /**
     * The identity's toString reaches log messages and, via ProxySecurityContextImpl, the message of an
     * AuthenticationException - which the exception mappers put in the HTTP response body. A full key
     * here goes back over the wire to whoever presented it.
     */
    @Test
    void testToStringNeverRendersTheWholeApiKey() {
        final ApiKeyUserIdentity identity = new ApiKeyUserIdentity(
                API_KEY,
                UserDesc.builder("someuser").build());

        final String rendered = identity.toString();

        assertThat(rendered).doesNotContain(API_KEY);
        assertThat(rendered).contains("sak_0123456789_");
        assertThat(rendered).doesNotContain("abcdefghijklmnopqrstuvwxyz");
        // getApiKey() is the accessor for code that genuinely needs the key, and is unaffected.
        assertThat(identity.getApiKey()).isEqualTo(API_KEY);
    }

    @Test
    void testAShortMalformedKeyIsNotRenderedAtAll() {
        final ApiKeyUserIdentity identity = new ApiKeyUserIdentity(
                "short",
                UserDesc.builder("someuser").build());

        assertThat(identity.toString()).doesNotContain("short");
    }

    /**
     * RFC 7235 makes the auth scheme case-insensitive. The previous implementation used
     * String.replace, which is both case-sensitive and would strip the text from anywhere in the
     * credential rather than only from the front.
     */
    @Test
    void testBearerPrefixIsStrippedRegardlessOfCase() {
        assertThat(ProxyUserIdentityFactory.stripBearerPrefix("Bearer " + API_KEY)).isEqualTo(API_KEY);
        assertThat(ProxyUserIdentityFactory.stripBearerPrefix("bearer " + API_KEY)).isEqualTo(API_KEY);
        assertThat(ProxyUserIdentityFactory.stripBearerPrefix("BEARER " + API_KEY)).isEqualTo(API_KEY);
        assertThat(ProxyUserIdentityFactory.stripBearerPrefix("  Bearer " + API_KEY + "  ")).isEqualTo(API_KEY);
    }

    @Test
    void testAKeyWithNoSchemeIsUnchanged() {
        assertThat(ProxyUserIdentityFactory.stripBearerPrefix(API_KEY)).isEqualTo(API_KEY);
    }

    @Test
    void testTheSchemeIsOnlyStrippedFromTheFront() {
        // String.replace would have removed this from the middle of the credential.
        final String awkward = "sak_0123456789_xxBearer yy";
        assertThat(ProxyUserIdentityFactory.stripBearerPrefix(awkward)).isEqualTo(awkward);
    }
}
