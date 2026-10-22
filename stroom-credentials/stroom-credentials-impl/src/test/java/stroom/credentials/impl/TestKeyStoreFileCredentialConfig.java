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

package stroom.credentials.impl;

import stroom.ai.shared.KeyStoreType;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestKeyStoreFileCredentialConfig {

    @Test
    void testKeyStoreTypeDefaultsToPkcs12() {
        assertThat(new KeyStoreFileCredentialConfig("keystore 1", null, "/certs/one.p12", null).getKeyStoreType())
                .isEqualTo(KeyStoreType.PKCS12);
    }

    @Test
    void testJsonRoundTrip() {
        final KeyStoreFileCredentialConfig config = new KeyStoreFileCredentialConfig("keystore 1",
                KeyStoreType.JKS,
                "/certs/one.jks",
                "pass phrase");

        final String json = JsonUtil.writeValueAsString(config);

        assertThat(JsonUtil.readValue(json, KeyStoreFileCredentialConfig.class)).isEqualTo(config);
    }

    @Test
    void testToStringDoesNotIncludeThePassPhrase() {
        final String str = new KeyStoreFileCredentialConfig("keystore 1",
                KeyStoreType.JKS,
                "/certs/one.jks",
                "my secret pass phrase").toString();

        assertThat(str)
                .contains("keystore 1")
                .contains("JKS")
                .contains("/certs/one.jks")
                .doesNotContain("my secret pass phrase");
    }

    @Test
    void testToStringShowsThereIsNoPassPhrase() {
        assertThat(new KeyStoreFileCredentialConfig("keystore 1", null, "/certs/one.p12", null).toString())
                .contains("keyStorePassword=null");
    }

    @Test
    void testEquality() {
        assertThat(new KeyStoreFileCredentialConfig("keystore 1", null, "/certs/one.p12", "a"))
                .isEqualTo(new KeyStoreFileCredentialConfig("keystore 1", KeyStoreType.PKCS12, "/certs/one.p12", "a"))
                .isNotEqualTo(new KeyStoreFileCredentialConfig("keystore 1", null, "/certs/one.p12", "b"))
                .isNotEqualTo(new KeyStoreFileCredentialConfig("keystore 2", null, "/certs/one.p12", "a"));
    }
}
