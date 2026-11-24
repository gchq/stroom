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

package stroom.security.impl.apikey;

import stroom.security.common.impl.hash.HashFunctionFactoryImpl;
import stroom.security.shared.HashAlgorithm;
import stroom.util.json.JsonUtil;
import stroom.util.string.StringUtil;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.core.type.TypeReference;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class TestApiKeyHasherFactoryImpl {

    /// A load of hard-coded inputs and their hash values to ensure that we don't accidentally change
    /// how the hashers work
    private static final String TEST_DATA = """
            [ {
              "inputText" : "7cYHWBjFS397LgWpCHQ9GFr65qjJECyagWQ9UVRq1rutB23fRMgWZcGrux3NXCifUQb66xPeupYTrfNC",
              "hash" : "S2sfxjtb2dsZGSdFPHBs8TfmTEuF736PSCsBQsfeotH",
              "hashAlgorithm" : "SHA3_256"
            }, {
              "inputText" : "YZxbwNF3t4xtPZh5Qkpbtc5kt9WYdfm54CSfBskbUf4W7hvF7t98sHSNixSyVmnpxPboJ5J8BSipkXRo",
              "hash" : "8CfU1GrDiSTf3R9XEto7zFZYHQoBDtJ9qaGphYZWyZWd",
              "hashAlgorithm" : "SHA3_256"
            }, {
              "inputText" : "91E1JQnvto1GM8yVuaMVYdtVybZo9VJcQsMB1fbN9HKVb8g13ZTaxihJ4w2jRD2BovtV9YHkuu9xmDyp",
              "hash" : "AiWCq97KRrrDaWqghXrqJiqjniAiLeZysgPTx9QnDJvo",
              "hashAlgorithm" : "SHA3_256"
            }, {
              "inputText" : "AXYAQmqG3zmERTaUnnZk4JjqML24PBWzp2JnxGaAp1cK7dEM1AsMPVCvb69izZeXFvXxbL2bqNSp33si",
              "hash" : "BNeZkJoV8tfA3nMZiLb4dRa2DYx1H69zsdZLqZ9iconJ",
              "hashAlgorithm" : "SHA3_256"
            }, {
              "inputText" : "mm4uj4EKbcfC3TKLWirXgGUQi7CbhdnppWqPcxdPHzyoY4hVVY5fHv5BnMKieQLipqZge5p8PBvGmaEK",
              "hash" : "ExntMTj8gpUdjQEYVu1PjuSyAEnmhpGTk27vmhgaDT2X",
              "hashAlgorithm" : "SHA3_256"
            }, {
              "inputText" : "dvC1HJiGKi8wLxbk34xiq2fCpG9KhA4TXMvHnjdgN6WLDocM9KdbAFAxvDKHdS4XBd8QboEu2vaQPLKe",
              "hash" : "8HMtetFDax3ZpdEx4AGGPpPEpsXhcqfnuihX9KPSQ8ke",
              "hashAlgorithm" : "SHA2_256"
            }, {
              "inputText" : "ytuaRnvbvb1RbnBjsP4KYVr49jZt4FuaC8iPHMfM1CPayeSWnpQuhQYNH3u58upqFLP9Sv9TFg3uUndJ",
              "hash" : "H7oCvzpLK7JTK7WCukD7bpPFoF14hTdrmRjLg5w6ciKn",
              "hashAlgorithm" : "SHA2_256"
            }, {
              "inputText" : "kkWjkLAq5559DqTDYNnmtrQQkR5LRsaH57RD9FX1wYx1RwB7FzuYZ89Pk6rSogPR9JgCjMNf9GTXEQFs",
              "hash" : "HXxPcVbf5JNqz4fky5V64M16Y9jA444Eu3tHfKRMVixc",
              "hashAlgorithm" : "SHA2_256"
            }, {
              "inputText" : "hBGUL3weocLKY27xxqeXJaEydPeWjCVNRESkYnfyccHUkQToLucEEzhBChzoHwRcBvLNwUq7rS4QNKbx",
              "hash" : "3K7fA4yNWaQFdfSYv5semSjRXNddr6sUxkhtTB8dPqyo",
              "hashAlgorithm" : "SHA2_256"
            }, {
              "inputText" : "bV1M3jumoQk8GrKgjD6fUtUBxsN472rGbBdY4NhnGKiM9Qe9Wr56nqXb3Fdcanph61F4w7RbdwGCJDw1",
              "hash" : "BSLX5GEuBNzPfoMLns6VUEhoTAEpqX7HWwCLnXpMNCon",
              "hashAlgorithm" : "SHA2_256"
            }, {
              "inputText" : "JPfTtrCW6g5jMDCo9x9wyThWi8SCzY2KkyacAMPNsqSnDqHCQCVy8AF7hQGTrtjBeNugn3GdLx15yoXA",
              "hash" : "$2a$10$BUEdp5MqNXsPBUnE30CGe.HXwMgsMTwRyVaQWsPb0.pkif.Wk6CBi",
              "hashAlgorithm" : "BCRYPT"
            }, {
              "inputText" : "SYTbeYKfFiMLrCxVACdU5sdb4efZCFPnQNnQn8Lf19MPnTLzLJhTNnfkZajGYv4Wh51m2qbsmjcBGgjg",
              "hash" : "$2a$10$Nnc33z.y2efqpCINW741quF.2n3FoRakBJteTQCxVxI7sYIe3YZf.",
              "hashAlgorithm" : "BCRYPT"
            }, {
              "inputText" : "hgJGrFQAFt7Xeq3mxR9rnGqBiRv5A1ytwW4K3FwgQ8wYAiPHbNujuTgj7onyRHGMdd6G6n92BUNoLnRR",
              "hash" : "$2a$10$aEEO9jSUxvhqTKPCGWhRX.DT5G8xnGkCwXNiNCvLgQfGWPAshKH/C",
              "hashAlgorithm" : "BCRYPT"
            }, {
              "inputText" : "t4brYp8Cyvi91CuQGT5q6TRpJqQmiYsgn5nWk7GpbwN7EqWGkZ8avuK4MGWfCS3njPjLVcusp6PRKGcz",
              "hash" : "$2a$10$HDShQ0axSGTHYWcSJHo0B.9zrttV9uKk06PziApF2F.jE2fDJdwba",
              "hashAlgorithm" : "BCRYPT"
            }, {
              "inputText" : "un2d12MgcRoXMAhd7pv1XYS6YVZm56U75XoXqjDqVcniNx1Q8kKTKrBiQq1GmQs2Vqd9ZHAWqeFKnEcQ",
              "hash" : "$2a$10$VbxZ7VK0jx.2RV9mulnwD.oSh9iBkBQVMyDi2mP/FIyz3lsJiA4FS",
              "hashAlgorithm" : "BCRYPT"
            }, {
              "inputText" : "7NZz5oeLZ1fGs6daLoD6k13yisdykf5Z6oMjmGt13x9kwvN2Rdw9SvNZHXPDJepGppKibVcZnyPrBPef",
              "hash" : "ib59ZbqLVHMerZ33ZhDmcUzEZDumjoD1ERh14updggeppLyxfWBDosJwtgcAtZWTf",
              "hashAlgorithm" : "ARGON_2"
            }, {
              "inputText" : "oC2iFa3yr55HtuZrXhutREpV7F11kueHGtwnUQXyEtL5CfVifgrsu1rNe8Hgt6n7iYzXhKEWq8GFL3GT",
              "hash" : "9hVCwiY9EhGt17Wnhc4ytnpGfNdASqCuyiMUkGtFKuzGhAoTkZzGP7Ydbm6PWNt8pq",
              "hashAlgorithm" : "ARGON_2"
            }, {
              "inputText" : "YqjuzPufHR2V5ifdA1uLRAGE1iR5BL9qVBNW1Ue3HUcgh1iBnN2hJPHUovdAvcGqQL4kmeDZxwo5JmmV",
              "hash" : "ANbK7M8kDuknFBXHZWjT8tNXW2gwThYvE6CQdaxjVyTe1YHgmRM8gwDMZZW6uoEogM",
              "hashAlgorithm" : "ARGON_2"
            }, {
              "inputText" : "keSgLcMqXphGjW9dtKG3L1x6SvTEj8z6wiNQM58rMgCsDpXmZUn56monxfQq3XApjkr9hi9t6p5BQU5d",
              "hash" : "2PYADoFWogoDtBe1FNfwgQJRaBJ8UHuuec1CBcqtgGVp1V1KjnsjJnCYgrg9Qh1ug7",
              "hashAlgorithm" : "ARGON_2"
            }, {
              "inputText" : "dyXGW7Cagr6vrS78cugX2UDB68hdcSzPpwkvNRy1uBLBzGwr3ZM32KmMbZsTYYdpwUwfDkShoqjKE2jV",
              "hash" : "3ySjKVeZM4dmEMFF6WCxiJTgJFQmXEqJukBNCdg1eTrdvukWhExn1cMD8vwZogFoWz",
              "hashAlgorithm" : "ARGON_2"
            }, {
              "inputText" : "3zSMykRW35SHWZzykEVMpuTddds1wh76Na3LKG4EatWhkrwbZetR4okfq2bqgdkZVZppvT4Uz4iHV1Bp",
              "hash" : "4Qn2VVnNgdVb3zYpMQ9kRHzUkJaUYT9DqY1tZShc89HLBywUZVkKbrFcTDCKnAjrA6BCCdznqv6xkWzJJJXoUS4w",
              "hashAlgorithm" : "SHA2_512"
            }, {
              "inputText" : "F2EKWEWmQixnfnhBehhaWU4UaoiCw15S4SpG44E5ufBki7Pc829HNAbedSQ9C6ZuZoayahaASBxtqhP3",
              "hash" : "2WrYkFsEkK1rgL8ZjKRh8jD29VZy8B3hKr3G3y2gKGhZCva1zcmzE6kcNLGy1g1dXGdAwMMUCPVaEy4gnGN3m2Ci",
              "hashAlgorithm" : "SHA2_512"
            }, {
              "inputText" : "fSxpQkts4x5cqECE2UxnnLvr7LYhWmBcDHMcrS4GeMqAqoDteTvvetHE2EhcdbB4QPwTqXQN7TKsLRrv",
              "hash" : "4uqCDX1UBrzPQdkWfwPdKh4SKCJgtJ2pcA2PKf3Yu2JeZJ47DyMA8dmgDJ4ASXuMPrWmSvMWRafnzDFmQJR2MLyF",
              "hashAlgorithm" : "SHA2_512"
            }, {
              "inputText" : "P38PHCoRw4dKVxpUDfhpLzY2hW9cQuJJdeYcw7tpmqDjisqZCHH74P8qGcQug6EGwaoLHWsgazjYhkwG",
              "hash" : "3bf8mBrhoG5GZweXup6ybzL7fMkdXa3GsWeU9YTmTFtqwQ5xQt9kZKnWjkfSWR4LnrBVKedACVPCex31oEakX8U5",
              "hashAlgorithm" : "SHA2_512"
            }, {
              "inputText" : "xqLgwR7Q92EGb8UoPfD2PKBQTmfp53bAQZhKcLTaN54e6tNJpEMnWGzxa2DPTkiEGs7kbAYdXGxABc2D",
              "hash" : "2kgdNHXG8mRaM6sQEwv8tAJQRps34CDR7yqTSURjNkDvLCHgvorcU18nGUmhsCoyw6NTGGkXmUg8yDTA2WmtsuWG",
              "hashAlgorithm" : "SHA2_512"
            } ]""";

    private final SecureRandom secureRandom = new SecureRandom();
    private final ApiKeyHasherFactoryImpl apiKeyHasherFactoryImpl = new ApiKeyHasherFactoryImpl(
            new HashFunctionFactoryImpl());

    @Disabled // Manual running only
    @Test
    void createTestData() {
        final int iterations = 5;
        final List<HashOutput> hashOutputs = new ArrayList<>(iterations * HashAlgorithm.values().length);
        for (final HashAlgorithm hashAlgorithm : HashAlgorithm.values()) {
            final ApiKeyHasher apiKeyHasher = apiKeyHasherFactoryImpl.getApiKeyHasher(hashAlgorithm);
            for (int i = 0; i < iterations; i++) {
                final String inputText = StringUtil.createRandomCode(secureRandom, 80);
                final String hash = apiKeyHasher.hash(inputText);
                hashOutputs.add(new HashOutput(inputText, hash, hashAlgorithm));
            }
        }
        final String json = JsonUtil.writeValueAsString(hashOutputs);
        System.out.println(json);
    }

    @TestFactory
    Stream<DynamicTest> testHashes() {
        final List<HashOutput> hashOutputs = JsonUtil.getMapper().readValue(
                TEST_DATA,
                new TypeReference<List<HashOutput>>() {
                });

        final Set<HashAlgorithm> algorithmsInData = hashOutputs.stream()
                .map(HashOutput::hashAlgorithm)
                .collect(Collectors.toSet());
        Assertions.assertThat(algorithmsInData)
                .withFailMessage("The test data is out of date, run createTestData() to update it.")
                .containsExactlyInAnyOrder(HashAlgorithm.values());

        final AtomicInteger counter = new AtomicInteger(0);
        return hashOutputs.stream()
                .map(hashOutput ->
                        DynamicTest.dynamicTest(
                                counter.incrementAndGet() + " - " + hashOutput.hashAlgorithm.name(),
                                () -> {
                                    final ApiKeyHasher apiKeyHasher = apiKeyHasherFactoryImpl.getApiKeyHasher(
                                            hashOutput.hashAlgorithm);
                                    final boolean isValidHash = apiKeyHasher.verify(
                                            hashOutput.inputText, hashOutput.hash);
                                    Assertions.assertThat(isValidHash)
                                            .isTrue();
                                }
                        )
                );
    }


    // --------------------------------------------------------------------------------


    private record HashOutput(
            String inputText,
            String hash,
            HashAlgorithm hashAlgorithm) {

    }
}
