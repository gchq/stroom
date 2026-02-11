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

package stroom.util.shared.string;

import stroom.test.common.TestUtil;
import stroom.test.common.TestUtil.TimedCase;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.concurrent.CopyOnWriteMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@ResourceLock(TestCIKeys.CI_KEYS_RESOURCE_LOCK)
@Execution(ExecutionMode.SAME_THREAD) // clearCommonKeys breaks other tests
class TestCIKeys {

    // Because some of the CIKey tests have to clear out the statically held CIKey
    // instances this can break other CIKey tests, so make them all use a resource lock.
    // Other tests that are indirectly using CIKeys should not be an issue as
    // CIKeys is just a tiny performance boost, so functionality should be OK.
    public static final String CI_KEYS_RESOURCE_LOCK = "CIKeysResourceLock";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestCIKeys.class);

    @BeforeEach
    void setUp() {
        // As we are dealing with a static map, one test may impact another, so always
        // start with an empty map.  Call addCommonKey to preload the map as required.
        CIKeys.clearCommonKeys();
    }

    @Test
    void test() {
        // ACCEPT is a common key, so any way we get/create it should give us the same string instances.
        CIKeys.addCommonKey(CIKeys.ACCEPT);
        final CIKey key1 = CIKeys.ACCEPT;
        final CIKey key2 = CIKey.of(key1.get());
        final CIKey key3 = CIKey.ofLowerCase(key1.getAsLowerCase());
        final CIKey key4 = CIKey.of("accept");
        // Explicitly don't try to get a common key
        final CIKey dynamicKey1 = CIKey.ofDynamicKey("accept");
        final CIKey dynamicKey2 = CIKey.ofDynamicKey("accept");

        assertThat(key1)
                .isSameAs(key2);
        assertThat(key1)
                .isSameAs(key3);
        assertThat(key1)
                .isSameAs(key4);

        // Different instances
        assertThat(key1)
                .isNotSameAs(dynamicKey1);
        assertThat(key1)
                .isNotSameAs(dynamicKey2);
        assertThat(dynamicKey1)
                .isNotSameAs(dynamicKey2);

        assertThat(key1.get())
                .isSameAs(key2.get());
        assertThat(key1.get())
                .isSameAs(key3.get());
        assertThat(key1.get())
                .isSameAs(key4.get());

        assertThat(key1.getAsLowerCase())
                .isSameAs(key2.getAsLowerCase());
        assertThat(key1.getAsLowerCase())
                .isSameAs(key3.getAsLowerCase());
        assertThat(key1.getAsLowerCase())
                .isSameAs(key4.getAsLowerCase());
    }

    @Test
    void testIntern() {
        // Use a uuid so we know it won't be in the static map
        final String key = UUID.randomUUID().toString().toUpperCase();

        assertThat(CIKeys.getCommonKey(key))
                .isNull();

        final CIKey ciKey1 = CIKeys.internCommonKey(key);

        // Intern again
        final CIKey ciKey2 = CIKeys.internCommonKey(key);

        assertThat(ciKey2)
                .isEqualTo(ciKey1);
        assertThat(ciKey2)
                .isSameAs(ciKey1);

        final CIKey ciKey3 = CIKeys.getCommonKey(key);

        assertThat(ciKey3)
                .isEqualTo(ciKey1);
        assertThat(ciKey3)
                .isSameAs(ciKey1);

        final CIKey ciKey4 = CIKeys.getCommonKeyByLowerCase(key.toLowerCase());

        assertThat(ciKey4)
                .isEqualTo(ciKey1);
        assertThat(ciKey4)
                .isSameAs(ciKey1);
    }

    @Test
    @Disabled
    void testMapPerf() {

        final int count = 5_000;
        final Map<String, String> concurrentMap = new ConcurrentHashMap<>();
        final Map<String, String> copyOnWriteMap = new CopyOnWriteMap<>();
        final List<String> uuids = IntStream.rangeClosed(1, count)
                .boxed()
                .map(i -> UUID.randomUUID().toString())
                .peek(uuid -> {
                    concurrentMap.put(uuid, uuid);
                    copyOnWriteMap.put(uuid, uuid);
                })
                .toList();

        final int cores = Runtime.getRuntime().availableProcessors();

        TestUtil.comparePerformance(
                5,
                100_000_000,
                LOGGER::info,
                TimedCase.of("concurrentMap", (round, iterations) -> {
                    TestUtil.multiThread(cores, () -> {
                        int j = 0;
                        for (int i = 0; i < iterations; i++) {
                            if (j >= count) {
                                j = 0;
                            }
                            concurrentMap.get(uuids.get(j));
                            j++;
                        }
                    });
                }),
                TimedCase.of("copyOnWriteMap", (round, iterations) -> {
                    TestUtil.multiThread(cores, () -> {
                        int j = 0;
                        for (int i = 0; i < iterations; i++) {
                            if (j >= count) {
                                j = 0;
                            }
                            copyOnWriteMap.get(uuids.get(j));
                            j++;
                        }
                    });
                })
        );
    }
}
