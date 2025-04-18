package stroom.receive.common;

import org.junit.jupiter.api.Test;

class TestArgon2DataFeedKeyHasher extends AbstractDataFeedKeyHasherTest {

    @Override
    boolean isSaltEncodedInHash() {
        return false;
    }

    @Override
    DataFeedKeyHasher getHasher() {
        return new Argon2DataFeedKeyHasher();
    }

    @Test
    void test1() {
        doHashTest("hello world");
    }

    @Test
    void test2() {
        final String input = "sdk_L8K8ttQc9Y7QtdrTX7PpYTXvDT5JJ9ZzR9pkTVHrAHbUaZXrDwmGMQfFW74o59L5dGSs5nnH" +
                             "unA8WboQ8Lbv4YENAYYM64p3P9zcZ5Xa5nfh8trqiyLWaFQ2gdQPinBL";
        doHashTest(input);
    }
}
