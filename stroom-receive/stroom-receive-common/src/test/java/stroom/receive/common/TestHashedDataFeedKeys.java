/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.receive.common;

import stroom.meta.api.StandardHeaderArguments;
import stroom.receive.common.DataFeedKeyGenerator.KeyWithHash;
import stroom.receive.common.DataFeedKeyHasher.HashOutput;
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
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestHashedDataFeedKeys {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestHashedDataFeedKeys.class);

    public static void main(final String[] args) throws IOException {
        final ObjectMapper mapper = JsonUtil.getMapper();
        final Path dir = Paths.get("/tmp/TestDataFeedKeys");
        Files.createDirectories(dir);

        final List<String> jsonList = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            final String fileName = "file" + i + ".json";
            final List<KeyWithHash> keyWithHashList = new ArrayList<>();
            final Instant expiry = switch (i) {
                case 0 -> Instant.now().minus(Duration.ofMinutes(10)); // Expired
                case 1 -> Instant.now().plus(Duration.ofMinutes(1)); // Soon to expire
                case 2 -> Instant.now().plus(Duration.ofDays(10)); // Long life
                default -> throw new RuntimeException("Unexpected i: " + i);
            };

            for (int j = 0; j < 3; j++) {
                final String accountId = String.valueOf(j + 1000);
                final KeyWithHash keyWithHash = DataFeedKeyGenerator.generateRandomKey(
                        accountId,
                        Map.of(
                                "MetaKey1", "MetaKey1Val-" + accountId,
                                "MetaKey2", "MetaKey2Val-" + accountId),
                        expiry);
                logKey("key" + i + "-" + j, keyWithHash);
                keyWithHashList.add(keyWithHash);
            }
            final HashedDataFeedKeys hashedDataFeedKeys = new HashedDataFeedKeys(keyWithHashList.stream()
                    .map(KeyWithHash::hashedDataFeedKey)
                    .toList());
            final Path filePath = dir.resolve(fileName);
            final String json = mapper.writeValueAsString(hashedDataFeedKeys);
            jsonList.add(json);
            Files.writeString(filePath, json, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
            LOGGER.info("JSON written to {}", filePath.toAbsolutePath());
        }

        for (final String json : jsonList) {
            LOGGER.info("json\n{}", json);
        }
    }

    private static void logKey(final String name, final KeyWithHash keyWithHash) {
        final String key = keyWithHash.key();
        final HashedDataFeedKey hashedDataFeedKey = keyWithHash.hashedDataFeedKey();
        LOGGER.info("""
                        name: {}, accountId: {}, expires in: {}:
                        {}
                        export TOKEN="{}"
                        """,
                name,
                hashedDataFeedKey.getStreamMetaValue(StandardHeaderArguments.ACCOUNT_ID),
                Duration.between(Instant.now(), hashedDataFeedKey.getExpiryDate()),
                key,
                key);
    }

    @Test
    void testSerde() throws IOException {
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

        final HashOutput hashOutput1 = hasher.hash(key1);
        final HashedDataFeedKey hashedDataFeedKey1 = new HashedDataFeedKey(
                hashOutput1.hash(),
                hashOutput1.salt(),
                hasher.getAlgorithm(),
                Map.of(
                        StandardHeaderArguments.ACCOUNT_ID, "system 1",
                        "key1", "val1",
                        "key2", "val2"),
                Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli());

        final HashOutput hashOutput2 = hasher.hash(key2);
        final HashedDataFeedKey hashedDataFeedKey2 = new HashedDataFeedKey(
                hashOutput2.hash(),
                hashOutput2.salt(),
                hasher.getAlgorithm(),
                Map.of(
                        StandardHeaderArguments.ACCOUNT_ID, "system 2",
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
