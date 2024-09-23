package stroom.query.common.v2;

import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;

import org.junit.jupiter.api.Test;

public class TestValHasher {

    @Test
    void test() {
        final ValHasher valHasher = new ValHasher(
                new DataWriterFactory(new ErrorConsumerImpl(), 1000));

        for (int i = 0; i < 1000; i++) {
            final Val val = ValString.create("Text " + i + "test".repeat(1000));
            valHasher.hash(Val.of(val));
        }
    }
}
