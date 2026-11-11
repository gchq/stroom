/*
 * Copyright 2025 Crown Copyright
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

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestBCryptDataFeedKeyHasher extends AbstractDataFeedKeyHasherTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestBCryptDataFeedKeyHasher.class);

    /// Hash/salt generated in 7.14-beta.5 using JBcrypt lib
    @SuppressWarnings("checkstyle:LineLength")
    private static final List<HashState> HASHES_7_14_BETA_5 = List.of(
            new HashState(
                    "hello world",
                    "$2a$10$MTuZviejYK3/.8Cz/SFfMOkQ2hoGnV01cHuneWVnBXcXMSnq4ZrCC",
                    "$2a$10$MTuZviejYK3/.8Cz/SFfMO"),
            new HashState(
                    "sdk_L8K8ttQc9Y7QtdrTX7PpYTXvDT5JJ9ZzR9pkTVHrAHbUaZXrDwmGMQfFW74o59L5dGSs5nnHunA8WboQ8Lbv4YENAYYM64p3P9zcZ5Xa5nfh8trqiyLWaFQ2gdQPinBL",
                    "$2a$10$v/cVxJyL4P3.RjUR7An2Cu3mPIKemq0TcyL4kzrivw.yrjD4sy9kS",
                    "$2a$10$v/cVxJyL4P3.RjUR7An2Cu"),
            new HashState(
                    "sdk_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "$2a$10$0Nvq9GIhBzs6E3JDPJmJ5et0OYvfRyc5e/IeHK3Mw4CUhzyicYZ9O",
                    "$2a$10$0Nvq9GIhBzs6E3JDPJmJ5e"));

    /// Hash/salt generated in 7.14-beta.12 using spring-security lib
    @SuppressWarnings("checkstyle:LineLength")
    private static final List<HashState> HASHES_7_14_BETA_12 = List.of(
            new HashState(
                    "hello world",
                    "$2a$10$HyKZxLWicGJNCUYjQTiF2esxeXQ83wFNpXbq4LkI2R5Vf.O580C.m",
                    "$2a$10$HyKZxLWicGJNCUYjQTiF2e"),
            new HashState(
                    "sdk_L8K8ttQc9Y7QtdrTX7PpYTXvDT5JJ9ZzR9pkTVHrAHbUaZXrDwmGMQfFW74o59L5dGSs5nnHunA8WboQ8Lbv4YENAYYM64p3P9zcZ5Xa5nfh8trqiyLWaFQ2gdQPinBL",
                    "$2a$10$jyMkBvzfeJO5d2HarJg6L.GODkWZbpvXBkTJWaiJKMU3QYKPvFZVm",
                    "$2a$10$jyMkBvzfeJO5d2HarJg6L."),
            new HashState(
                    "sdk_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "$2a$10$.kEie10cipXOP7vfvRtqAeTyaQz/l2.XkpsdykeJnaK4Dy67Pg8/S",
                    "$2a$10$.kEie10cipXOP7vfvRtqAe"));

    /// Hash/salt generated in 7.14-beta.13 using spring-security lib (after truncation changes)
    @SuppressWarnings("checkstyle:LineLength")
    private static final List<HashState> HASHES_7_14_BETA_13 = List.of(
            new HashState(
                    "hello world",
                    "$2a$10$bVplG9z1s4Hy.Ojw8Gany.aLlw6nxuUT/Shcsgl5.wEOQSfYXlP5C",
                    "$2a$10$bVplG9z1s4Hy.Ojw8Gany."),
            new HashState(
                    "sdk_L8K8ttQc9Y7QtdrTX7PpYTXvDT5JJ9ZzR9pkTVHrAHbUaZXrDwmGMQfFW74o59L5dGSs5nnHunA8WboQ8Lbv4YENAYYM64p3P9zcZ5Xa5nfh8trqiyLWaFQ2gdQPinBL",
                    "$2a$10$SmDwgPLLtPLvXLIhsum9/uIsPd/2Jwd2tmxVoNcD9VtCpsYqGetRO",
                    "$2a$10$SmDwgPLLtPLvXLIhsum9/u"),
            new HashState(
                    "sdk_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "$2a$10$7OQAZMZat10N0yYe38STXeNPcv5HlJUfwUyKqaji2xW7Vlj9cYMm.",
                    "$2a$10$7OQAZMZat10N0yYe38STXe"));


    @Override
    boolean isSaltEncodedInHash() {
        return true;
    }

    @Override
    DataFeedKeyHasher getHasher() {
        return new BCryptDataFeedKeyHasher();
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

    @Test
    void testLongKey() {
        final String input = "sdk_" + "a".repeat(128);
        doHashTest(input);
    }

    /// Make sure we can verify hashed made in 7.14-beta.5 (using JBcrypt lib)
    @Test
    void test_7_14_BETA_5() {
        final DataFeedKeyHasher hasher = getHasher();
        for (final HashState hashState : HASHES_7_14_BETA_5) {
            final boolean isValid = hasher.verify(hashState.input, hashState.hash, hashState.salt);
            assertThat(isValid)
                    .isTrue();

            // Also make sure that if we make a new hash/salt, we can verify that too
            doHashTest(hashState.input);
        }
    }

    /// Make sure we can verify hashed made in 7.14-beta.12 (using Spring Security lib)
    @Test
    void test_7_14_BETA_12() {
        final DataFeedKeyHasher hasher = getHasher();
        for (final HashState hashState : HASHES_7_14_BETA_12) {
            final boolean isValid = hasher.verify(hashState.input, hashState.hash, hashState.salt);
            assertThat(isValid)
                    .isTrue();

            // Also make sure that if we make a new hash/salt, we can verify that too
            doHashTest(hashState.input);
        }
    }

    /// Make sure we can verify hashed made in 7.14-beta.13 (using Spring Security lib,
    /// with fixed input truncation)
    @Test
    void test_7_14_BETA_13() {
        final DataFeedKeyHasher hasher = getHasher();
        for (final HashState hashState : HASHES_7_14_BETA_13) {
            final boolean isValid = hasher.verify(hashState.input, hashState.hash, hashState.salt);
            assertThat(isValid)
                    .isTrue();

            // Also make sure that if we make a new hash/salt, we can verify that too
            doHashTest(hashState.input);
        }
    }


    // --------------------------------------------------------------------------------


    private record HashState(String input, String hash, String salt) {

    }
}
