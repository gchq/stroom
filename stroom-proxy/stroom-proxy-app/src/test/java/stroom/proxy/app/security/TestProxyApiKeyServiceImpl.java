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

import stroom.proxy.app.DownstreamHostConfig;
import stroom.proxy.app.ProxyConfig;
import stroom.security.api.HashFunction;
import stroom.security.common.impl.ApiKeyGenerator;
import stroom.security.mock.MockCommonSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppPermissionSet;
import stroom.security.shared.HashAlgorithm;
import stroom.security.shared.VerifyApiKeyRequest;
import stroom.util.io.SimplePathCreator;
import stroom.util.shared.UserDesc;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestProxyApiKeyServiceImpl {

    // A deterministic test hash - the real algorithm is irrelevant to the permission-matching logic.
    private static final HashFunction TEST_HASH = new HashFunction() {
        @Override
        public String generateSalt() {
            return null;
        }

        @Override
        public String hash(final String value, final String salt) {
            return "hash:" + value;
        }

        @Override
        public HashAlgorithm getType() {
            return HashAlgorithm.SHA2_256;
        }
    };

    private final String apiKey = new ApiKeyGenerator().generateRandomApiKey();
    private final String prefix = ApiKeyGenerator.extractPrefixPart(apiKey);
    private final String hash = TEST_HASH.hash(apiKey);

    @Test
    void matchesWhenKeyAndPermissionsMatch() {
        final AppPermissionSet perms = AppPermission.STROOM_PROXY.asAppPermissionSet();
        assertThat(ProxyApiKeyServiceImpl.localEntryMatches(
                apiKey, perms, hash, prefix, perms, TEST_HASH))
                .isTrue();
    }

    @Test
    void doesNotMatchWhenRequiredPermissionsDiffer() {
        // The escalation guard: a persisted entry verified for an EMPTY permission set must NOT satisfy a
        // request that requires STROOM_PROXY, even though the key (hash + prefix) is identical.
        assertThat(ProxyApiKeyServiceImpl.localEntryMatches(
                apiKey,
                AppPermission.STROOM_PROXY.asAppPermissionSet(),
                hash,
                prefix,
                AppPermissionSet.empty(),
                TEST_HASH))
                .isFalse();
    }

    @Test
    void doesNotMatchWhenKeyDiffers() {
        // A different key (whose hash does not match the persisted entry) must not match, regardless of perms.
        final String otherKey = new ApiKeyGenerator().generateRandomApiKey();
        assertThat(ProxyApiKeyServiceImpl.localEntryMatches(
                otherKey,
                AppPermissionSet.empty(),
                hash,
                prefix,
                AppPermissionSet.empty(),
                TEST_HASH))
                .isFalse();
    }

    /**
     * No test constructed this class - all three above call the package-private static
     * {@code localEntryMatches}, which pins the permission-escalation guard and nothing else. The two
     * behaviours that landed were exercised by nothing at all, and both are about what the
     * proxy does when it cannot get an authoritative answer, which is exactly where a security
     * component must not guess.
     * <p>
     * <strong>A negative verdict is cached.</strong> {@code isTooOld} used to treat every entry with a
     * null value as permanently stale, so an unauthenticated caller sending well-formed but unknown
     * keys forced one downstream verification <em>and one full rewrite of the persisted key file</em>
     * per request, all serialised on this instance's monitor. A free denial-of-service against a
     * proxy's own downstream.
     * </p>
     */
    @Test
    void testAnUnknownKeyIsVerifiedOnceAndThenAnsweredFromCache(@TempDir final Path tempDir)
            throws Exception {
        final ProxyApiKeyCheckClient client = Mockito.mock(ProxyApiKeyCheckClient.class);
        Mockito.when(client.fetchApiKeyValidity(Mockito.any())).thenReturn(Optional.empty());

        final ProxyApiKeyServiceImpl service = createService(tempDir, client, StroomDuration.ofMinutes(5));
        final VerifyApiKeyRequest request = new VerifyApiKeyRequest(apiKey, AppPermissionSet.empty());

        assertThat(service.verifyApiKey(request)).isEmpty();
        assertThat(service.verifyApiKey(request)).isEmpty();

        Mockito.verify(client, Mockito.times(1)).fetchApiKeyValidity(Mockito.any());
    }

    /**
     * The other half, and the more dangerous one. When the downstream cannot answer and there
     * is nothing on disk, the proxy does <strong>not</strong> know the key is invalid — and caching
     * that "no" as authoritative would go on rejecting a good key for {@code maxCachedKeyAge} after
     * the downstream recovered. Nothing is cached, so the next attempt asks again.
     */
    @Test
    void testAnUnavailableDownstreamIsNotCachedAsAnInvalidKey(@TempDir final Path tempDir)
            throws Exception {
        final ProxyApiKeyCheckClient client = Mockito.mock(ProxyApiKeyCheckClient.class);
        Mockito.when(client.fetchApiKeyValidity(Mockito.any()))
                .thenThrow(new RuntimeException("downstream unavailable"))
                .thenReturn(Optional.of(UserDesc.builder("someone").build()));

        // No back-off, so the second attempt is allowed to reach the downstream at once.
        final ProxyApiKeyServiceImpl service = createService(tempDir, client, StroomDuration.ZERO);
        final VerifyApiKeyRequest request = new VerifyApiKeyRequest(apiKey, AppPermissionSet.empty());

        assertThat(service.verifyApiKey(request))
                .as("the downstream could not answer, so neither can the proxy")
                .isEmpty();
        assertThat(service.verifyApiKey(request))
                .as("and the recovered downstream must be believed, not a cached guess")
                .isPresent();
    }

    private ProxyApiKeyServiceImpl createService(final Path tempDir,
                                                 final ProxyApiKeyCheckClient client,
                                                 final StroomDuration noFetchIntervalAfterFailure) {
        final DownstreamHostConfig downstreamHostConfig = DownstreamHostConfig.builder()
                .withEnabled(true)
                .withHostname("downstream.example.com")
                .withMaxCachedKeyAge(StroomDuration.ofMinutes(5))
                .withNoFetchIntervalAfterFailure(noFetchIntervalAfterFailure)
                .build();

        return new ProxyApiKeyServiceImpl(
                () -> downstreamHostConfig,
                new ApiKeyGenerator(),
                () -> ProxyConfig.builder().build(),
                MockCommonSecurityContext::new,
                () -> client,
                algorithm -> TEST_HASH,
                new SimplePathCreator(() -> tempDir, () -> tempDir));
    }

}
