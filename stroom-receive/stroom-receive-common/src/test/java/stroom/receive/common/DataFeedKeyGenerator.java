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
import stroom.receive.common.DataFeedKeyHasher.HashOutput;
import stroom.util.shared.NullSafe;
import stroom.util.string.StringUtil;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

public class DataFeedKeyGenerator {

    private static final DataFeedKeyHasher HASHER = new BCryptDataFeedKeyHasher();

    public static KeyWithHash generateRandomKey(final String accountId,
                                                final Map<String, String> attributeMap,
                                                final Instant expiry) {
        final String key =
                "sdk_"
                + StringUtil.createRandomCode(
                        new SecureRandom(),
                        DataFeedKeyServiceImpl.DATA_FEED_KEY_RANDOM_PART_LENGTH,
                        StringUtil.ALLOWED_CHARS_BASE_58_STYLE);

        final Map<String, String> attrMap = new HashMap<>(NullSafe.map(attributeMap));
        attrMap.put(StandardHeaderArguments.ACCOUNT_ID, accountId);
        final HashOutput hashOutput = HASHER.hash(key);

        return new KeyWithHash(key, new HashedDataFeedKey(
                hashOutput.hash(),
                hashOutput.salt(),
                HASHER.getAlgorithm(),
                attrMap,
                expiry.toEpochMilli()));
    }

    public static KeyWithHash generateFixedTestKey1() {
        final DataFeedKeyHasher hasher = new Argon2DataFeedKeyHasher();
        final String key1 =
                "sdk_"
                + "okfXqkmtns3k4828fZcnutWUFmegj3hqk83o9sYCLefWGTrRrpT6Bt23FuT1ebwcftPNaL" +
                "1B7aFbK37gbpefZgQeeP3esbnvNXu612co4awVxpn33He6i1vn7g8kUFEk";

        final HashOutput hashOutput = hasher.hash(key1);

        final HashedDataFeedKey hashedDataFeedKey1 = new HashedDataFeedKey(
                hashOutput.hash(),
                hashOutput.salt(),
                hasher.getAlgorithm(),
                Map.of(
                        StandardHeaderArguments.ACCOUNT_ID, "1234",
                        "key1", "val1",
                        "key2", "val2"),
                Instant.now().plus(10, ChronoUnit.DAYS).toEpochMilli());

        return new KeyWithHash(key1, hashedDataFeedKey1);
    }

    public static KeyWithHash generateFixedTestKey2() {
        final DataFeedKeyHasher hasher = new Argon2DataFeedKeyHasher();
        final String key2 =
                "sdk_"
                + hasher.getAlgorithm().getUniqueId()
                + "_"
                + "7GqxzCAhBnui4wSCicVtFdmghBxtBAQVDbLrsqDAqthuoHTmVEorJf6xvWviWajwKboJUD" +
                "vanQXK8UpYroqwfxxYhsG264acXbjcpeQPutNqXrq3rTNqWWYNWaQrj2e1";
        final HashOutput hashOutput = hasher.hash(key2);

        final HashedDataFeedKey hashedDataFeedKey2 = new HashedDataFeedKey(
                hashOutput.hash(),
                hashOutput.salt(),
                hasher.getAlgorithm(),
                Map.of(
                        StandardHeaderArguments.ACCOUNT_ID, "6789",
                        "key3", "val3",
                        "key4", "val4"),
                Instant.now().plus(10, ChronoUnit.DAYS).toEpochMilli());

        return new KeyWithHash(key2, hashedDataFeedKey2);
    }


    // --------------------------------------------------------------------------------


    public record KeyWithHash(String key, HashedDataFeedKey hashedDataFeedKey) {

    }
}
