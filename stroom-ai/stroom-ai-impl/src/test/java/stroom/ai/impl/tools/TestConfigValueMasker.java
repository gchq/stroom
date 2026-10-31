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

package stroom.ai.impl.tools;

import stroom.config.global.shared.ConfigProperty;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestConfigValueMasker {

    private static final String MASK = ConfigValueMasker.MASK;

    @Test
    void testAPasswordIsAlwaysMasked() {
        final ConfigProperty property = property("stroom.smtp.pass", "String").password(true).build();

        assertThat(ConfigValueMasker.isSecret(property)).isTrue();
        assertThat(ConfigValueMasker.mask(property, "anything")).isEqualTo(MASK);
        assertThat(ConfigValueMasker.mask(property, null)).isNull();
    }

    @Test
    void testANameLikeASecretIsMaskedUnlessItsTypeCannotHoldOne() {
        assertThat(ConfigValueMasker.isSecret(property("stroom.openId.clientSecret", "String").build())).isTrue();
        assertThat(ConfigValueMasker.isSecret(property("stroom.x.secretEncryptionKey", "String").build())).isTrue();
        assertThat(ConfigValueMasker.isSecret(property("stroom.x.apiToken", "String").build())).isTrue();
        assertThat(ConfigValueMasker.isSecret(property("stroom.x.maxApiKeyExpiryAge", "StroomDuration").build()))
                .isFalse();
        assertThat(ConfigValueMasker.isSecret(property("stroom.x.formTokenRequest", "Boolean").build())).isFalse();
        // Only a name ending in token is taken as one, so a token endpoint URL is not.
        assertThat(ConfigValueMasker.isSecret(property("stroom.x.tokenEndpoint", "String").build())).isFalse();
    }

    @Test
    void testCredentialsInAValueAreMasked() {
        assertThat(ConfigValueMasker.maskEmbedded("https://bob:hunter2@host/path"))
                .isEqualTo("https://bob:" + MASK + "@host/path");
        assertThat(ConfigValueMasker.maskEmbedded("jdbc:mysql://db/stroom?user=stroom&password=pw1&ssl=true"))
                .isEqualTo("jdbc:mysql://db/stroom?user=stroom&password=" + MASK + "&ssl=true");
        assertThat(ConfigValueMasker.maskEmbedded("{\"apiKey\": \"k-123\", \"name\": \"x\"}"))
                .isEqualTo("{\"apiKey\": \"" + MASK + "\", \"name\": \"x\"}");
        assertThat(ConfigValueMasker.maskEmbedded("<secret>abc</secret><user>bob</user>"))
                .isEqualTo("<secret>" + MASK + "</secret><user>bob</user>");
        assertThat(ConfigValueMasker.maskEmbedded("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9"))
                .doesNotContain("eyJhbGciOiJIUzI1NiJ9");
        assertThat(ConfigValueMasker.maskEmbedded("ssl.truststore.password: changeit\nretries=3"))
                .isEqualTo("ssl.truststore.password: " + MASK + "\nretries=3");
        assertThat(ConfigValueMasker.maskEmbedded("1000000,100,10,1")).isEqualTo("1000000,100,10,1");
    }

    @Test
    void testTheSecretsOfAMapAreMasked() {
        final ConfigProperty property = property("stroom.x.headers", "Map of String, String").build();

        assertThat(ConfigValueMasker.mask(property, "|=user=bob|password=s3|url=http://a:b@h"))
                .isEqualTo("|=user=bob|password=" + MASK + "|url=http://a:" + MASK + "@h");
        // Not written as a map: masked as any other value.
        assertThat(ConfigValueMasker.mask(property, "password=s3")).isEqualTo("password=" + MASK);
    }

    private static ConfigProperty.Builder property(final String path, final String type) {
        return ConfigProperty.builder().name(path).dataTypeName(type);
    }
}
