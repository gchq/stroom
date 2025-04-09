package stroom.receive.common;

import stroom.receive.common.DataFeedKeyHasher.HashOutput;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractDataFeedKeyHasherTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractDataFeedKeyHasherTest.class);

    protected void doHashTest(final String input) {
        final DataFeedKeyHasher hasher = getHasher();
        final HashOutput hashOutput = hasher.hash(input);
        LOGGER.info("""
                input: {}
                hash: {}
                salt: {}""", input, hashOutput.hash(), hashOutput.salt());

        final String salt = isSaltEncodedInHash()
                ? null
                : hashOutput.salt();

        assertThat(hasher.verify(input, hashOutput.hash(), salt))
                .isEqualTo(true);
    }

    abstract DataFeedKeyHasher getHasher();

    abstract boolean isSaltEncodedInHash();
}
