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
