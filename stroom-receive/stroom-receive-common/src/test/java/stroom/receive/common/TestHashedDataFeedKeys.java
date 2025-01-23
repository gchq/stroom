package stroom.receive.common;

import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestHashedDataFeedKeys {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestHashedDataFeedKeys.class);

    public static void main(String[] args) throws IOException {
        final DataFeedKeyHasher hasher = new Argon2DataFeedKeyHasher();

        @SuppressWarnings("checkstyle:lineLength") final String key1 =
                "sdk_"
                + hasher.getAlgorithm().getUniqueId()
                + "_"
                + "okfXqkmtns3k4828fZcnutWUFmegj3hqk83o9sYCLefWGTrRrpT6Bt23FuT1ebwcftPNaL1B7aFbK37gbpefZgQeeP3esbnvNXu612co4awVxpn33He6i1vn7g8kUFEk";
        @SuppressWarnings("checkstyle:lineLength") final String key2 =
                "sdk_"
                + hasher.getAlgorithm().getUniqueId()
                + "_"
                + "7GqxzCAhBnui4wSCicVtFdmghBxtBAQVDbLrsqDAqthuoHTmVEorJf6xvWviWajwKboJUDvanQXK8UpYroqwfxxYhsG264acXbjcpeQPutNqXrq3rTNqWWYNWaQrj2e1";

        final HashedDataFeedKey hashedDataFeedKey1 = new HashedDataFeedKey(
                hasher.hash(key1),
                hasher.getAlgorithm().getUniqueId(),
                "datafeed-key-user1",
                "user 1",
                "1234",
                Map.of(
                        "key1", "val1",
                        "key2", "val2"),
                Instant.now().plus(10, ChronoUnit.DAYS).toEpochMilli());

        final HashedDataFeedKey hashedDataFeedKey2 = new HashedDataFeedKey(
                hasher.hash(key2),
                hasher.getAlgorithm().getUniqueId(),
                "datafeed-key-user2",
                "user 2",
                "6789",
                Map.of(
                        "key3", "val3",
                        "key4", "val4"),
                Instant.now().plus(10, ChronoUnit.DAYS).toEpochMilli());

        final HashedDataFeedKeys hashedDataFeedKeys = new HashedDataFeedKeys(List.of(
                hashedDataFeedKey1,
                hashedDataFeedKey2));

        final ObjectMapper mapper = JsonUtil.getMapper();
        final Path dir = Paths.get("/tmp/TestDataFeedKeys");
        Files.createDirectories(dir);
        final Path filePath = dir.resolve("file1.json");
        final String json = mapper.writeValueAsString(hashedDataFeedKeys);
        Files.writeString(filePath, json, StandardOpenOption.WRITE, StandardOpenOption.CREATE);

        LOGGER.info("key1:\n{}", key1);
        LOGGER.info("key2:\n{}", key2);
        LOGGER.info("json\n{}", json);
        LOGGER.info("JSON written to {}", filePath.toAbsolutePath());
    }

    @Test
    void testSerde() throws IOException {
        final DataFeedKeyHasher hasher = new Argon2DataFeedKeyHasher();

        @SuppressWarnings("checkstyle:lineLength") final String key1 = "sdk_"
                                                                       + hasher.getAlgorithm().getUniqueId()
                                                                       + "_"
                                                                       + "okfXqkmtns3k4828fZcnutWUFmegj3hqk83o9sYCLefWGTrRrpT6Bt23FuT1ebwcftPNaL1B7aFbK37gbpefZgQeeP3esbnvNXu612co4awVxpn33He6i1vn7g8kUFEk";
        @SuppressWarnings("checkstyle:lineLength") final String key2 = "sdk_"
                                                                       + hasher.getAlgorithm().getUniqueId()
                                                                       + "_"
                                                                       + "7GqxzCAhBnui4wSCicVtFdmghBxtBAQVDbLrsqDAqthuoHTmVEorJf6xvWviWajwKboJUDvanQXK8UpYroqwfxxYhsG264acXbjcpeQPutNqXrq3rTNqWWYNWaQrj2e1";

        final HashedDataFeedKey hashedDataFeedKey1 = new HashedDataFeedKey(
                hasher.hash(key1),
                hasher.getAlgorithm().getUniqueId(),
                "user1",
                "user 1",
                "system 1",
                Map.of(
                        "key1", "val1",
                        "key2", "val2"),
                Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli());

        final HashedDataFeedKey hashedDataFeedKey2 = new HashedDataFeedKey(
                hasher.hash(key2),
                hasher.getAlgorithm().getUniqueId(),
                "user2",
                "user 2",
                "system 2",
                Map.of(
                        "key3", "val3",
                        "key4", "val4"),
                Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli());
        final HashedDataFeedKeys hashedDataFeedKeys = new HashedDataFeedKeys(List.of(
                hashedDataFeedKey1,
                hashedDataFeedKey2));

        doSerdeTest(hashedDataFeedKeys, HashedDataFeedKeys.class);
    }

    private <T> void doSerdeTest(final T entity,
                                 final Class<T> clazz) throws IOException {

        final ObjectMapper mapper = JsonUtil.getMapper();
        assertThat(mapper.canSerialize(entity.getClass()))
                .isTrue();

        final String json = mapper.writeValueAsString(entity);
        System.out.println("\n" + json);

        final T entity2 = mapper.readValue(json, clazz);

        assertThat(entity2)
                .isEqualTo(entity);
    }
}
