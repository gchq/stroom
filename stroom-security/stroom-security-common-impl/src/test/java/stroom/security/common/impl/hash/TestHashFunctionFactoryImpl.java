/*
 * Copyright 2026 Crown Copyright
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

package stroom.security.common.impl.hash;

import stroom.security.api.HashFunction;
import stroom.security.shared.HashAlgorithm;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestHashFunctionFactoryImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestHashFunctionFactoryImpl.class);

    @TestFactory
    Stream<DynamicTest> testAllAlgorithms() {
        final HashFunctionFactoryImpl hashFunctionFactory = new HashFunctionFactoryImpl();
        return Arrays.stream(HashAlgorithm.values())
                .map(hashAlgorithm ->
                        DynamicTest.dynamicTest(hashAlgorithm.name(), () ->
                                testHashAlgorithm(hashFunctionFactory, hashAlgorithm)));
    }

    private void testHashAlgorithm(final HashFunctionFactoryImpl hashFunctionFactory,
                                   final HashAlgorithm hashAlgorithm) {

        final HashFunction hashFunction = hashFunctionFactory.getHashFunction(hashAlgorithm);
        final String salt = hashFunction.generateSalt();

        final String hash1 = hashFunction.hash("foo", salt);
        final String hash2 = hashFunction.hash("foo");
        final String hash3 = hashFunction.hash("foo", salt);

        LOGGER.debug("Testing hashAlgorithm {}, salt: {}, hash: {}", hashAlgorithm, salt, hash1);

        assertThat(hash2)
                .isNotEqualTo(hash1);
        assertThat(hash3)
                .isEqualTo(hash1);

        assertThat(hashFunction.verify("foo", hash1))
                .isFalse();
        assertThat(hashFunction.verify("foo", hash1, salt))
                .isTrue();
        // Salt is encoded in the hash with bcrypt, so bad salt is ignored
        if (hashAlgorithm != HashAlgorithm.BCRYPT) {
            assertThat(hashFunction.verify("foo", hash1, "bad salt"))
                    .isFalse();
        }
        assertThat(hashFunction.verify("fooX", hash1, salt))
                .isFalse();
    }

    @SuppressWarnings("checkstyle:LineLength")
    @Test
    void testLegacyBcryptHashes() {

        // A set of inputs with their hashes (produced by JBcrypt to $2a$ spec)
        final List<InputAndHash> inputsAndHashes = List.of(
                new InputAndHash(
                        "sdk_p9FXn5WhHJEufxPuDMVjHn8XCozenx5qkcNRagRUwbMqVjByFwoMPfPFFeynLjRFfwYMsH47XE93TfEs6oMoSrPBKHiG9H7XSr5hWas9cNKXNCdSLayAZL8q9gAn3K51",
                        "$2a$10$6fYYJo0IoY89HqrqwujO8.IrS5iwUIGAD6sMLvffIbXQV65rn5YtO"),
                new InputAndHash(
                        "sdk_3UvzKThyuobKG9FnpSRLTcNBP8pxChViFN6sSAdNhyEZgjwDSSd1WMXY8Y7zNxW1JbssAZzZig6HcyLHEqmZnfckkSJg5cT7q9f6B3doBfdfCkfCz5TVFByES2yQ6etw",
                        "$2a$10$iUwqGaVqDxjzBkInY4/aHOMVcn7E8GFSx3I4LR4wj6dFgWA5Mlr7y"),
                new InputAndHash(
                        "sdk_3WtAKKXenwqY6n7JThbSkx6T7FRqrkrNkJCZ2RFL92WAV7JwbGn4hKPt6wGCSNpZ5PmbV8Vg5X84UmhDex42GPxDL2CR1c3sukR6xq6ob3UJPcotY6MgoEgu5QFTothR",
                        "$2a$10$W8jfx1nIXWlbPceHgRg5zONJOXi5P6X.KHaT7Pgl6o6IqWmp8K.Ta"),
                new InputAndHash(
                        "sdk_7YYMrWXXfD8TkgvksakVUFhmjsbWEvav7N4ip25ZADdukSLpnW4DvNZtb6azzmd4Xa8f4Kf9RbF5G5xXAtyPWnGy9xQSGozoEYgbCsvoVUcksxiqHm8uS2KLGpfVA2qf",
                        "$2a$10$LS4RRR1O8Z7aS7jwOiLYYObUfDob0ZMlVglQroDjybP1ghm97ymqu"),
                new InputAndHash(
                        "sdk_6spTs34iqQHLdWhYiuYot7MK3EAK1s378CZbmbvAwGDrzswmNTnkfSG9DAXLDuABJgP6d4SA2HvPehfxAD2P6x96n7wAPJLteFEVsf5dsPTufLN2roTRLiq6WiUweqX6",
                        "$2a$10$HPwdsRo8VrbgQZqK2TsiLOywyE/LXlEjD.LYN1lfZwqJLuznvkkiy"));

        testBcryptHashes(inputsAndHashes);
    }

    @SuppressWarnings("checkstyle:LineLength")
    @Test
    void testNewBcryptHashes() {

        // A set of inputs with their hashes (produced by CyberChef to $2b$ spec)
        final List<InputAndHash> inputsAndHashes = List.of(
                new InputAndHash(
                        "sdk_p9FXn5WhHJEufxPuDMVjHn8XCozenx5qkcNRagRUwbMqVjByFwoMPfPFFeynLjRFfwYMsH47XE93TfEs6oMoSrPBKHiG9H7XSr5hWas9cNKXNCdSLayAZL8q9gAn3K51",
                        "$2b$10$9gyFtfgJB.EuyfLxiPKr2eKyIF5EA7E7psCW9Za8vge3YvtvkISfS"),
                new InputAndHash(
                        "sdk_3UvzKThyuobKG9FnpSRLTcNBP8pxChViFN6sSAdNhyEZgjwDSSd1WMXY8Y7zNxW1JbssAZzZig6HcyLHEqmZnfckkSJg5cT7q9f6B3doBfdfCkfCz5TVFByES2yQ6etw",
                        "$2b$10$SyxLeFERNWyy0mSSNM.qu.XAyu6XH044ZVo8xcA2KyYCSZjNvrRCy"),
                new InputAndHash(
                        "sdk_3WtAKKXenwqY6n7JThbSkx6T7FRqrkrNkJCZ2RFL92WAV7JwbGn4hKPt6wGCSNpZ5PmbV8Vg5X84UmhDex42GPxDL2CR1c3sukR6xq6ob3UJPcotY6MgoEgu5QFTothR",
                        "$2b$10$gFbCi8aq2L0mWOfisKU7.u8RI5GCj1dFdOzwf/Rci8dGVWeJl1.Eq"),
                new InputAndHash(
                        "sdk_7YYMrWXXfD8TkgvksakVUFhmjsbWEvav7N4ip25ZADdukSLpnW4DvNZtb6azzmd4Xa8f4Kf9RbF5G5xXAtyPWnGy9xQSGozoEYgbCsvoVUcksxiqHm8uS2KLGpfVA2qf",
                        "$2b$10$u2qu8DvCHYgfkqVPGHYZIu4dzfUsDMy06nHruTvOwXtKAFNgG7Oa6"),
                new InputAndHash(
                        "sdk_6spTs34iqQHLdWhYiuYot7MK3EAK1s378CZbmbvAwGDrzswmNTnkfSG9DAXLDuABJgP6d4SA2HvPehfxAD2P6x96n7wAPJLteFEVsf5dsPTufLN2roTRLiq6WiUweqX6",
                        "$2b$10$Fm0ifvMxSvDERueF8ItnpeYlr1X/wvWz7Ij7BP1Gs6rkeJ4xr3iO."));

        testBcryptHashes(inputsAndHashes);
    }

    private static void testBcryptHashes(final List<InputAndHash> legacyHashes) {
        final HashFunctionFactoryImpl hashFunctionFactory = new HashFunctionFactoryImpl();
        final HashFunction hashFunction = hashFunctionFactory.getHashFunction(HashAlgorithm.BCRYPT);

        for (final InputAndHash inputAndHash : legacyHashes) {
            boolean isValid = hashFunction.verify(inputAndHash.input(), inputAndHash.hash(), "ignored");
            assertThat(isValid)
                    .isTrue();
            isValid = hashFunction.verify(inputAndHash.input(), inputAndHash.hash(), "ignored");
            assertThat(isValid)
                    .isTrue();
        }
    }


    // --------------------------------------------------------------------------------


    private record InputAndHash(String input, String hash) {

    }
}
