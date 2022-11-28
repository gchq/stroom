package stroom.security.impl;

import stroom.security.common.impl.JwtUtil;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestJwtUtil {
    @Test
    void testGetUserIdFromIdentities() {
        final String identities = "" +
                "[{" + "" +
                "\"userId\":\"test_user@somewhere.com\"," +
                "\"providerName\":\"UNKNOWN\"," +
                "\"providerType\":\"UNKNOWN\"," +
                "\"issuer\":\"https://some.issuer.com\"," +
                "\"primary\":true," +
                "\"dateCreated\":12345" +
                "}]";
        final String userId = JwtUtil.getUserIdFromIdentities(identities);
        assertThat(userId).isEqualTo("test_user@somewhere.com");
    }

    @Test
    void testRemovePrefix() {
        final String prefixed = "corp_test_user@somewhere.com";
        final String userId = JwtUtil.removePrefix(prefixed);
        assertThat(userId).isEqualTo("test_user@somewhere.com");
    }
}
