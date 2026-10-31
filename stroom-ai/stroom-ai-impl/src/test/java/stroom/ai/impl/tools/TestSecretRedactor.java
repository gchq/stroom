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

import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestSecretRedactor {

    @Test
    void aClustersCredentialsAreRedacted() {
        // An Elastic cluster's connection: its credentials are never given to the model.
        final Object doc = Map.of("name", "ES_PROD", "connection", Map.of(
                "connectionUrls", List.of("https://es:9200"),
                "apiKeySecret", "s3cret",
                "caCertificate", "-----BEGIN"));

        assertThat(SecretRedactor.redact(doc)).isEqualTo(Map.of("name", "ES_PROD", "connection", Map.of(
                "connectionUrls", List.of("https://es:9200"),
                "apiKeySecret", SecretRedactor.REDACTED,
                "caCertificate", SecretRedactor.REDACTED)));
    }

    @Test
    void anEmptySecretIsLeftAsItIs() {
        assertThat(SecretRedactor.redact(Map.of("password", "", "list", List.of(Map.of("password", "p")))))
                .isEqualTo(Map.of("password", "", "list", List.of(Map.of("password", SecretRedactor.REDACTED))));
    }

    @Test
    void aJsonPartIsRedactedAndAnyOtherLeftAsItIs() {
        final String json = "{\"name\":\"ES_PROD\",\"connection\":{\"apiKeySecret\":\"s3cret\"}}";

        final String redacted = SecretRedactor.redactJson(json);
        assertThat(redacted).doesNotContain("s3cret").contains(SecretRedactor.REDACTED);
        assertThat(JsonUtil.readValue(redacted, Map.class)).containsEntry("name", "ES_PROD");
        // Nothing to redact, or not JSON: the text exactly as it was.
        final String plain = "{ \"name\" : \"x\" }";
        assertThat(SecretRedactor.redactJson(plain)).isSameAs(plain);
        final String xslt = "<xsl:param name=\"password\"/>";
        assertThat(SecretRedactor.redactJson(xslt)).isSameAs(xslt);
        final String broken = "{\"password\": ";
        assertThat(SecretRedactor.redactJson(broken)).isSameAs(broken);
    }
}
