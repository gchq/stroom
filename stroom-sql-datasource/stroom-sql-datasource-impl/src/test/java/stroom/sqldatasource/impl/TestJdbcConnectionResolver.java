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

package stroom.sqldatasource.impl;

import stroom.credentials.api.StoredSecret;
import stroom.credentials.api.StoredSecrets;
import stroom.credentials.shared.AccessTokenSecret;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.Secret;
import stroom.credentials.shared.UsernamePasswordSecret;
import stroom.sqldatasource.shared.JdbcConnectionSettings;
import stroom.util.shared.PermissionException;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class TestJdbcConnectionResolver {

    private static final String CREDENTIAL_NAME = "hr-read-only";

    private final StoredSecrets storedSecrets = Mockito.mock(StoredSecrets.class);

    private JdbcConnectionResolver resolver(final int maxPoolSizeLimit) {
        final SqlDataSourceConfig config = new SqlDataSourceConfig(
                List.of(FakeJdbcDriver.ALLOWED_URL_PATTERN), null, null, null, null, maxPoolSizeLimit, null, null,
                null, null, null);
        return new JdbcConnectionResolver(() -> config, () -> storedSecrets);
    }

    private static JdbcConnectionSettings.Builder settings() {
        return JdbcConnectionSettings.builder()
                .url(FakeJdbcDriver.URL);
    }

    private void givenSecret(final Secret secret) {
        final StoredSecret storedSecret = new StoredSecret(Mockito.mock(Credential.class), secret, null);
        Mockito.when(storedSecrets.get(CREDENTIAL_NAME)).thenReturn(storedSecret);
    }

    @Test
    void resolvesUsernameAndPasswordFromTheCredential() {
        givenSecret(new UsernamePasswordSecret("reader", "s3cret"));

        final ResolvedJdbcConnection resolved = resolver(20).resolve(settings()
                .credentialName(CREDENTIAL_NAME)
                .connectionProperties(Map.of("useCursorFetch", "true"))
                .build());

        assertThat(resolved.url()).isEqualTo(FakeJdbcDriver.URL);
        assertThat(resolved.username()).isEqualTo("reader");
        assertThat(resolved.password()).isEqualTo("s3cret");
        assertThat(resolved.connectionProperties()).containsEntry("useCursorFetch", "true");
        assertThat(resolved.maxPoolSize()).isEqualTo(JdbcConnectionSettings.DEFAULT_MAX_POOL_SIZE);
    }

    @Test
    void noCredentialMeansNoUsernameOrPassword() {
        final ResolvedJdbcConnection resolved = resolver(20).resolve(settings().build());

        assertThat(resolved.username()).isNull();
        assertThat(resolved.password()).isNull();
        Mockito.verifyNoInteractions(storedSecrets);
    }

    @Test
    void poolSizeIsCappedByTheAdministrator() {
        final ResolvedJdbcConnection resolved = resolver(3).resolve(settings()
                .maxPoolSize(50)
                .build());

        assertThat(resolved.maxPoolSize()).isEqualTo(3);
    }

    @Test
    void poolSizeIsAtLeastOne() {
        final ResolvedJdbcConnection resolved = resolver(20).resolve(settings()
                .maxPoolSize(0)
                .build());

        assertThat(resolved.maxPoolSize()).isEqualTo(1);
    }

    @Test
    void missingCredentialIsReported() {
        assertThatThrownBy(() -> resolver(20).resolve(settings()
                .credentialName(CREDENTIAL_NAME)
                .build()))
                .isInstanceOf(PermissionException.class)
                .hasMessageContaining("Unable to use credential 'hr-read-only'");
    }

    @Test
    void missingAndForbiddenCredentialsCannotBeToldApart() {
        // Otherwise a document editor could find out which credential names exist.
        final Throwable missing = catchThrowable(() -> resolver(20).resolve(settings()
                .credentialName(CREDENTIAL_NAME)
                .build()));
        Mockito.when(storedSecrets.get(CREDENTIAL_NAME))
                .thenThrow(new PermissionException(null, "You do not have permission to view these credentials"));
        final Throwable forbidden = catchThrowable(() -> resolver(20).resolve(settings()
                .credentialName(CREDENTIAL_NAME)
                .build()));

        assertThat(forbidden)
                .isInstanceOf(missing.getClass())
                .hasMessage(missing.getMessage());
    }

    @Test
    void credentialOfTheWrongTypeIsReported() {
        givenSecret(new AccessTokenSecret("token"));

        assertThatThrownBy(() -> resolver(20).resolve(settings()
                .credentialName(CREDENTIAL_NAME)
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not a username and password");
    }

    @Test
    void userWithoutUsePermissionOnTheCredentialIsRefused() {
        // StoredSecrets checks Use permission as the current user and throws if it is missing.
        Mockito.when(storedSecrets.get(CREDENTIAL_NAME))
                .thenThrow(new PermissionException(null, "You do not have permission to view these credentials"));

        assertThatThrownBy(() -> resolver(20).resolve(settings()
                .credentialName(CREDENTIAL_NAME)
                .build()))
                .isInstanceOf(PermissionException.class);
    }

    @Test
    void urlNotAllowedIsRefusedBeforeTheCredentialIsRead() {
        assertThatThrownBy(() -> resolver(20).resolve(JdbcConnectionSettings.builder()
                .url("jdbc:mysql://elsewhere:3306/hr")
                .credentialName(CREDENTIAL_NAME)
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
        Mockito.verifyNoInteractions(storedSecrets);
    }

    @Test
    void toStringLeavesOutThePassword() {
        final ResolvedJdbcConnection resolved = new ResolvedJdbcConnection(
                FakeJdbcDriver.URL, "reader", "s3cret", Map.of("token", "t0ken"), 1);

        assertThat(resolved.toString())
                .contains("reader")
                .doesNotContain("s3cret")
                .doesNotContain("t0ken");
    }
}
