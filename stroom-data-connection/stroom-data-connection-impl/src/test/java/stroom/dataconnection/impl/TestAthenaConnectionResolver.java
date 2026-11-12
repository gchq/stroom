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

package stroom.dataconnection.impl;

import stroom.credentials.api.StoredSecret;
import stroom.credentials.api.StoredSecrets;
import stroom.credentials.shared.AwsKeySecret;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.Secret;
import stroom.credentials.shared.UsernamePasswordSecret;
import stroom.dataconnection.shared.AthenaConnectionSettings;
import stroom.util.shared.PermissionException;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestAthenaConnectionResolver {

    private static final String CREDENTIAL_NAME = "athena-reader";

    private final StoredSecrets storedSecrets = Mockito.mock(StoredSecrets.class);
    private final AthenaConnectionResolver resolver = new AthenaConnectionResolver(
            () -> new DataConnectionConfig(null, null, null, null, null, null, null, null,
                    List.of("^eu-west-2/.*$"), null, true, null, null),
            () -> storedSecrets);

    private void givenSecret(final Secret secret) {
        Mockito.when(storedSecrets.get(CREDENTIAL_NAME))
                .thenReturn(new StoredSecret(Mockito.mock(Credential.class), secret, null));
    }

    private static AthenaConnectionSettings.Builder settings() {
        return AthenaConnectionSettings.builder()
                .region("eu-west-2");
    }

    @Test
    void defaultsAreApplied() {
        final ResolvedAthenaConnection connection = resolver.resolve(settings()
                .workGroup(null)
                .catalog(" ")
                .database(null)
                .build());

        assertThat(connection.region()).isEqualTo("eu-west-2");
        assertThat(connection.workGroup()).isEqualTo(AthenaConnectionSettings.DEFAULT_WORK_GROUP);
        assertThat(connection.catalog()).isEqualTo(AthenaConnectionSettings.DEFAULT_CATALOG);
        assertThat(connection.database()).isEqualTo(AthenaConnectionSettings.DEFAULT_DATABASE);
        assertThat(connection.outputLocation()).isNull();
        assertThat(connection.resultReuseMinutes()).isZero();
    }

    @Test
    void noCredentialMeansStroomsOwnIdentity() {
        final ResolvedAthenaConnection connection = resolver.resolve(settings().build());

        assertThat(connection.awsKey()).isNull();
        Mockito.verifyNoInteractions(storedSecrets);
    }

    @Test
    void credentialGivesTheAccessKey() {
        givenSecret(new AwsKeySecret("AKIAEXAMPLE", "s3cret"));

        final ResolvedAthenaConnection connection = resolver.resolve(settings()
                .credentialName(CREDENTIAL_NAME)
                .build());

        assertThat(connection.awsKey()).isEqualTo(new AwsKeySecret("AKIAEXAMPLE", "s3cret"));
        assertThat(connection.toString())
                .contains("AKIAEXAMPLE")
                .doesNotContain("s3cret");
    }

    @Test
    void credentialOfTheWrongTypeIsReported() {
        givenSecret(new UsernamePasswordSecret("reader", "s3cret"));

        assertThatThrownBy(() -> resolver.resolve(settings()
                .credentialName(CREDENTIAL_NAME)
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not an AWS access key");
    }

    @Test
    void missingOrForbiddenCredentialIsRefused() {
        assertThatThrownBy(() -> resolver.resolve(settings()
                .credentialName(CREDENTIAL_NAME)
                .build()))
                .isInstanceOf(PermissionException.class)
                .hasMessageContaining("Unable to use credential 'athena-reader'");
    }

    @Test
    void workGroupNotAllowedIsRefusedBeforeTheCredentialIsRead() {
        assertThatThrownBy(() -> resolver.resolve(AthenaConnectionSettings.builder()
                .region("us-east-1")
                .credentialName(CREDENTIAL_NAME)
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowedAthenaWorkGroups");
        Mockito.verifyNoInteractions(storedSecrets);
    }
}
