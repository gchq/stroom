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
import stroom.credentials.shared.AwsKeySecret;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.UsernamePasswordSecret;
import stroom.util.shared.PermissionException;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class TestCredentialSecrets {

    private static final String NAME = "athena-reader";

    private final StoredSecrets storedSecrets = Mockito.mock(StoredSecrets.class);

    private AwsKeySecret get() {
        return CredentialSecrets.get(storedSecrets, NAME, AwsKeySecret.class, "an AWS access key");
    }

    @Test
    void secretOfTheRightTypeIsReturned() {
        final AwsKeySecret secret = new AwsKeySecret("AKIAEXAMPLE", "s3cret");
        Mockito.when(storedSecrets.get(NAME)).thenReturn(new StoredSecret(Mockito.mock(Credential.class),
                secret, null));

        assertThat(get()).isEqualTo(secret);
    }

    @Test
    void secretOfTheWrongTypeIsNamed() {
        Mockito.when(storedSecrets.get(NAME)).thenReturn(new StoredSecret(
                Mockito.mock(Credential.class), new UsernamePasswordSecret("reader", "s3cret"), null));

        assertThatThrownBy(this::get)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Credential 'athena-reader' is not an AWS access key")
                .hasMessageNotContaining("s3cret");
    }

    @Test
    void missingAndForbiddenCredentialsCannotBeToldApart() {
        // Otherwise a document editor could find out which credential names exist.
        final Throwable missing = catchThrowable(this::get);
        Mockito.when(storedSecrets.get(NAME))
                .thenThrow(new PermissionException(null, "You do not have permission to view these credentials"));
        final Throwable forbidden = catchThrowable(this::get);

        assertThat(missing).isInstanceOf(PermissionException.class);
        assertThat(forbidden)
                .isInstanceOf(PermissionException.class)
                .hasMessage(missing.getMessage());
    }
}
