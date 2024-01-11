package stroom.util.string;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Random;

class TestBase58 {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestBase58.class);

    @Test
    void testEncodeDecode() {
        final String input = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed " +
                "do eiusmod tempor incididunt ut labore et dolore magna aliqua. ";
        final String base58 = Base58.encode(input.getBytes(StandardCharsets.UTF_8));

        final String output = new String(Base58.decode(base58), StandardCharsets.UTF_8);

        Assertions.assertThat(output)
                .isEqualTo(input);
    }

    /**
     * Encode/decode may random strings of different lengths to make sure it is reversible
     */
    @Test
    void testEncodeDecode2() {
        final SecureRandom secureRandom = new SecureRandom();
        final Random random = new Random();
        for (int i = 0; i < 100; i++) {
            final String input = StringUtil.createRandomCode(secureRandom, random.nextInt(100) + 1);
            final byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
            final String base58 = Base58.encode(inputBytes);
            final byte[] outputBytes = Base58.decode(base58);

            Assertions.assertThat(outputBytes)
                    .isEqualTo(inputBytes);

            final String output = new String(outputBytes);

            Assertions.assertThat(output)
                    .isEqualTo(input);
        }
    }
}
