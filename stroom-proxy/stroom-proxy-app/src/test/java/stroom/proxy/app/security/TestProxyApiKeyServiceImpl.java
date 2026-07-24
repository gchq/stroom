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

package stroom.proxy.app.security;

import stroom.security.api.HashFunction;
import stroom.security.common.impl.ApiKeyGenerator;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppPermissionSet;
import stroom.security.shared.HashAlgorithm;

import org.junit.jupiter.api.Test;

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
}
