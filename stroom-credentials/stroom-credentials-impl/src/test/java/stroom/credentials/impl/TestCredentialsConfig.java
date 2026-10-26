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
import stroom.security.shared.User;
import stroom.test.common.util.test.TestingHomeAndTempProvidersModule;
import stroom.util.config.AppConfigValidator;
import stroom.util.config.ConfigValidator.Result;
import stroom.util.json.JsonUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.UserType;
import stroom.util.validation.ValidationModule;

import com.google.inject.Guice;
import jakarta.validation.ConstraintViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestCredentialsConfig {

    @Test
    void testDefaults() {
        final CredentialsConfig config = new CredentialsConfig();

        assertThat(config.getKeyStoreFiles()).isEmpty();
        assertThat(config.getKeyStoreFilesOwnerSubjectId()).isEqualTo(User.ADMINISTRATORS_GROUP_SUBJECT_ID);
        assertThat(config.getKeyStoreFilesOwnerType()).isEqualTo(UserType.GROUP);
    }

    @Test
    void testReadKeyStoreFiles() {
        final CredentialsConfig config = JsonUtil.readValue("""
                {
                  "allowedKeyStoreFileDirs": ["/etc/stroom/certs"],
                  "keyStoreFiles": [
                    {
                      "name": "keystore 1",
                      "keyStoreType": "JKS",
                      "keyStorePath": "/etc/stroom/certs/one.jks",
                      "keyStorePassword": "pass phrase"
                    },
                    {
                      "name": "keystore 2",
                      "keyStorePath": "/etc/stroom/certs/two.p12"
                    }
                  ],
                  "keyStoreFilesOwnerSubjectId": "jbloggs",
                  "keyStoreFilesOwnerType": "USER"
                }""", CredentialsConfig.class);

        assertThat(config.getKeyStoreFiles()).containsExactly(
                new KeyStoreFileCredentialConfig("keystore 1",
                        KeyStoreType.JKS,
                        "/etc/stroom/certs/one.jks",
                        "pass phrase"),
                new KeyStoreFileCredentialConfig("keystore 2",
                        KeyStoreType.PKCS12,
                        "/etc/stroom/certs/two.p12",
                        null));
        assertThat(config.getKeyStoreFilesOwnerSubjectId()).isEqualTo("jbloggs");
        assertThat(config.getKeyStoreFilesOwnerType()).isEqualTo(UserType.USER);
    }

    @Test
    void testOwnerDefaultsWhenOnlyKeyStoreFilesAreSet() {
        final CredentialsConfig config = JsonUtil.readValue("""
                {
                  "keyStoreFiles": [
                    { "name": "keystore 1", "keyStorePath": "/etc/stroom/certs/one.p12" }
                  ]
                }""", CredentialsConfig.class);

        assertThat(config.getKeyStoreFilesOwnerSubjectId()).isEqualTo(User.ADMINISTRATORS_GROUP_SUBJECT_ID);
        assertThat(config.getKeyStoreFilesOwnerType()).isEqualTo(UserType.GROUP);
    }

    @Test
    void testToStringDoesNotIncludeThePassPhrase() {
        final CredentialsConfig config = new CredentialsConfig(null,
                null,
                null,
                List.of(new KeyStoreFileCredentialConfig("keystore 1",
                        null,
                        "/etc/stroom/certs/one.p12",
                        "my secret pass phrase")),
                null,
                null);

        assertThat(config.toString())
                .contains("keystore 1")
                .contains("/etc/stroom/certs/one.p12")
                .doesNotContain("my secret pass phrase");
    }

    @Test
    void testKeyStoreFilesWithoutANameOrPathAreInvalid(@TempDir final Path tempDir) {
        final AppConfigValidator validator = Guice.createInjector(
                        new TestingHomeAndTempProvidersModule(tempDir),
                        new ValidationModule())
                .getInstance(AppConfigValidator.class);
        final CredentialsConfig config = new CredentialsConfig(null,
                null,
                null,
                List.of(
                        new KeyStoreFileCredentialConfig("keystore 1", null, "/etc/stroom/certs/one.p12", null),
                        new KeyStoreFileCredentialConfig(" ", null, null, null)),
                null,
                null);

        final Result<AbstractConfig> result = validator.validate(config);

        final List<String> messages = new ArrayList<>();
        result.handleViolations((violation, severity) -> messages.add(violation.getMessage()));
        assertThat(messages).containsExactlyInAnyOrder(
                "Each key store file credential must have a name",
                "Each key store file credential must have a keyStorePath");
    }

    @Test
    void testValidKeyStoreFilesHaveNoViolations(@TempDir final Path tempDir) {
        final AppConfigValidator validator = Guice.createInjector(
                        new TestingHomeAndTempProvidersModule(tempDir),
                        new ValidationModule())
                .getInstance(AppConfigValidator.class);
        final CredentialsConfig config = new CredentialsConfig(null,
                null,
                null,
                List.of(new KeyStoreFileCredentialConfig("keystore 1", null, "/etc/stroom/certs/one.p12", null)),
                null,
                null);

        final Result<AbstractConfig> result = validator.validate(config);

        final List<ConstraintViolation<? extends AbstractConfig>> violations = new ArrayList<>();
        result.handleViolations((violation, severity) -> violations.add(violation));
        assertThat(violations).isEmpty();
    }
}
