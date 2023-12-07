package stroom.util.string;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

class TestBase58 {

    @Test
    void testEncodeDecode() {
        final String input = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed " +
                "do eiusmod tempor incididunt ut labore et dolore magna aliqua. ";
        final String base58 = Base58.encode(input.getBytes(StandardCharsets.UTF_8));

        final String output = new String(Base58.decode(base58), StandardCharsets.UTF_8);

        Assertions.assertThat(output)
                .isEqualTo(input);
    }
}
