package stroom.receive.common;

import stroom.util.string.StringUtil;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

public class DataFeedKeyGenerator {

    final DataFeedKeyHasher hasher = new Argon2DataFeedKeyHasher();

    public static KeyWithHash generateRandomKey(final String subjectId,
                                                final String displayName,
                                                final String accountId,
                                                final Map<String, String> attributeMap,
                                                final Instant expiry) {
        final DataFeedKeyHasher hasher = new Argon2DataFeedKeyHasher();
        final String key =
                "sdk_"
                + hasher.getAlgorithm().getUniqueId()
                + "_"
                + StringUtil.createRandomCode(
                        new SecureRandom(), 128, StringUtil.ALLOWED_CHARS_BASE_58_STYLE);

        return new KeyWithHash(key, new HashedDataFeedKey(
                hasher.hash(key),
                hasher.getAlgorithm().getUniqueId(),
                accountId,
                attributeMap,
                expiry.toEpochMilli()));
    }

    public static KeyWithHash generateFixedTestKey1() {
        final DataFeedKeyHasher hasher = new Argon2DataFeedKeyHasher();
        @SuppressWarnings("checkstyle:lineLength") final String key1 =
                "sdk_"
                + hasher.getAlgorithm().getUniqueId()
                + "_"
                + "okfXqkmtns3k4828fZcnutWUFmegj3hqk83o9sYCLefWGTrRrpT6Bt23FuT1ebwcftPNaL1B7aFbK37gbpefZgQeeP3esbnvNXu612co4awVxpn33He6i1vn7g8kUFEk";

        final HashedDataFeedKey hashedDataFeedKey1 = new HashedDataFeedKey(
                hasher.hash(key1),
                hasher.getAlgorithm().getUniqueId(),
                "1234",
                Map.of(
                        "key1", "val1",
                        "key2", "val2"),
                Instant.now().plus(10, ChronoUnit.DAYS).toEpochMilli());

        return new KeyWithHash(key1, hashedDataFeedKey1);
    }

    public static KeyWithHash generateFixedTestKey2() {
        final DataFeedKeyHasher hasher = new Argon2DataFeedKeyHasher();
        @SuppressWarnings("checkstyle:lineLength") final String key2 =
                "sdk_"
                + hasher.getAlgorithm().getUniqueId()
                + "_"
                + "7GqxzCAhBnui4wSCicVtFdmghBxtBAQVDbLrsqDAqthuoHTmVEorJf6xvWviWajwKboJUDvanQXK8UpYroqwfxxYhsG264acXbjcpeQPutNqXrq3rTNqWWYNWaQrj2e1";

        final HashedDataFeedKey hashedDataFeedKey2 = new HashedDataFeedKey(
                hasher.hash(key2),
                hasher.getAlgorithm().getUniqueId(),
                "6789",
                Map.of(
                        "key3", "val3",
                        "key4", "val4"),
                Instant.now().plus(10, ChronoUnit.DAYS).toEpochMilli());

        return new KeyWithHash(key2, hashedDataFeedKey2);
    }


    // --------------------------------------------------------------------------------


    public record KeyWithHash(String key, HashedDataFeedKey hashedDataFeedKey) {

    }
}
