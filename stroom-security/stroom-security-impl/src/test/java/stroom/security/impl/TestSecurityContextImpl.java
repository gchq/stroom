package stroom.security.impl;

import stroom.security.api.SecurityContext;
import stroom.security.impl.exception.AuthenticationException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestSecurityContextImpl {

    @Test
    void test() {
        assertThatThrownBy(() -> {
            final SecurityContext securityContext = new SecurityContextImpl(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
            securityContext.secure(Assertions::fail);
        }).isInstanceOf(AuthenticationException.class);
    }
}
