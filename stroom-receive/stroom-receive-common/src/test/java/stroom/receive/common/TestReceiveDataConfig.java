package stroom.receive.common;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestReceiveDataConfig {

    @Test
    void testBuilder() {
        // Attempt to check that the no-args ctor produces the same default config
        // as the builder with no modifications. Relies on equals method being
        // up-to-date though
        final ReceiveDataConfig receiveDataConfig1 = new ReceiveDataConfig();
        final ReceiveDataConfig receiveDataConfig2 = ReceiveDataConfig.builder()
                .build();

        Assertions.assertThat(receiveDataConfig1)
                .isEqualTo(receiveDataConfig2);
    }
}
