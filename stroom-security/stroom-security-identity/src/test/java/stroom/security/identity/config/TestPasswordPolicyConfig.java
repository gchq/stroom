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

import stroom.test.common.TestUtil;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestPasswordPolicyConfig {

    @Test
    void testSerDeser_default() {

        final PasswordPolicyConfig passwordPolicyConfig = new PasswordPolicyConfig();
        TestUtil.testSerialisation(passwordPolicyConfig, PasswordPolicyConfig.class);
    }

    @Test
    void testSerDeser_falseBooleans() {

        final PasswordPolicyConfig passwordPolicyConfig = new PasswordPolicyConfig(
                false,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                "foo");
        TestUtil.testSerialisation(passwordPolicyConfig, PasswordPolicyConfig.class);
    }

    @Test
    void testDefaults() {
        final PasswordPolicyConfig passwordPolicyConfig1 = new PasswordPolicyConfig();
        final PasswordPolicyConfig passwordPolicyConfig2 = new PasswordPolicyConfig(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        Assertions.assertThat(passwordPolicyConfig2)
                .isEqualTo(passwordPolicyConfig1);
    }
}
