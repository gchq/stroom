/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.security.identity.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestIdentityConfig {

    @Test
    void passwordResetRequestCooldownIsAlwaysSupplied() {
        // Config yaml that leaves the property out deserialises through the JsonCreator constructor with
        // a null, and resetEmail uses it on every request, so it must never be null.
        final IdentityConfig config = new IdentityConfig(
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);

        assertThat(config.getPasswordResetRequestCooldown()).isNotNull();
        assertThat(config.getPasswordResetRequestCooldown().toMillis()).isPositive();
    }

    @Test
    void newFlagsDefaultToOff() {
        // Both features must stay off unless deliberately enabled.
        final IdentityConfig config = new IdentityConfig();

        assertThat(config.isReactivateInactiveAccountsOnLogin()).isFalse();
        assertThat(config.isAllowLockedAccountPasswordReset()).isFalse();
    }
}
