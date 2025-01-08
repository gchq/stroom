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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestDataFeedKeys {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestDataFeedKeys.class);

    public static void main(String[] args) throws IOException {
        final DataFeedKeyHasher hasher = new Argon2DataFeedKeyHasher();

        @SuppressWarnings("checkstyle:lineLength") final String key1 = "sdk_"
                + hasher.getAlgorithm().getUniqueId()
                + "_"
                + "okfXqkmtns3k4828fZcnutWUFmegj3hqk83o9sYCLefWGTrRrpT6Bt23FuT1ebwcftPNaL1B7aFbK37gbpefZgQeeP3esbnvNXu612co4awVxpn33He6i1vn7g8kUFEk";
        @SuppressWarnings("checkstyle:lineLength") final String key2 = "sdk_"
                + hasher.getAlgorithm().getUniqueId()
                + "_"
                + "7GqxzCAhBnui4wSCicVtFdmghBxtBAQVDbLrsqDAqthuoHTmVEorJf6xvWviWajwKboJUDvanQXK8UpYroqwfxxYhsG264acXbjcpeQPutNqXrq3rTNqWWYNWaQrj2e1";

        final DataFeedKey dataFeedKey1 = new DataFeedKey(
                hasher.hash(key1),
                hasher.getAlgorithm().getDisplayValue(),
                "user1",
                "user 1",
                "system 1",
                Map.of(
                        "key1", "val1",
                        "key2", "val2"),
                Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli());

        final DataFeedKey dataFeedKey2 = new DataFeedKey(
                hasher.hash(key2),
                hasher.getAlgorithm().getDisplayValue(),
                "user2",
                "user 2",
                "system 2",
                Map.of(
                        "key3", "val3",
                        "key4", "val4"),
                Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli());

        final DataFeedKeys dataFeedKeys = new DataFeedKeys(List.of(
                dataFeedKey1,
                dataFeedKey2));

        final ObjectMapper mapper = JsonUtil.getMapper();
        final Path dir = Paths.get("/tmp/TestDataFeedKeys");
        Files.createDirectories(dir);
        final Path filePath = dir.resolve("file1.json");
        mapper.writeValue(filePath.toFile(), dataFeedKeys);

        LOGGER.info("key1:\n{}", key1);
        LOGGER.info("key2:\n{}", key2);
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

        final DataFeedKey dataFeedKey1 = new DataFeedKey(
                hasher.hash(key1),
                hasher.getAlgorithm().getUniqueId(),
                "user1",
                "user 1",
                "system 1",
                Map.of(
                        "key1", "val1",
                        "key2", "val2"),
                Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli());

        final DataFeedKey dataFeedKey2 = new DataFeedKey(
                hasher.hash(key2),
                hasher.getAlgorithm().getUniqueId(),
                "user2",
                "user 2",
                "system 2",
                Map.of(
                        "key3", "val3",
                        "key4", "val4"),
                Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli());
        final DataFeedKeys dataFeedKeys = new DataFeedKeys(List.of(
                dataFeedKey1,
                dataFeedKey2));

        doSerdeTest(dataFeedKeys, DataFeedKeys.class);
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
