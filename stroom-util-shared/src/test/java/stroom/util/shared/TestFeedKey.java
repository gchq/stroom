/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.util.shared;

import stroom.test.common.TestUtil;

import io.vavr.Tuple;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

class TestFeedKey {

    @TestFactory
    Stream<DynamicTest> testEquality() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(FeedKey.class, FeedKey.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final FeedKey key1 = testCase.getInput()._1();
                    final FeedKey key2 = testCase.getInput()._2();

                    final boolean areEqual = Objects.equals(key1, key2);
                    if (areEqual && testCase.getExpectedOutput() && NullSafe.allNonNull(key1, key2)) {
                        Assertions.assertThat(key1.hashCode())
                                .isEqualTo(key2.hashCode());
                    }
                    return areEqual;
                })
                .withSimpleEqualityAssertion()
                // --- EQUALITY CASES ---
                .addCase(
                        Tuple.of(FeedKey.of("FEED_A", "TYPE_1"), FeedKey.of("FEED_A", "TYPE_1")),
                        true)
                .addCase(
                        Tuple.of(FeedKey.of("FEED_A", null), FeedKey.of("FEED_A", null)),
                        true)
                .addCase(
                        Tuple.of(FeedKey.of(null, "TYPE_1"), FeedKey.of(null, "TYPE_1")),
                        true)
                .addCase(
                        Tuple.of(FeedKey.of(null, null), FeedKey.of(null, null)),
                        true)
                .addCase(
                        Tuple.of(null, null),
                        true)

                // --- INEQUALITY CASES ---
                .addCase(
                        Tuple.of(FeedKey.of("FEED_A", "TYPE_1"), FeedKey.of("FEED_B", "TYPE_1")),
                        false)
                .addCase(
                        Tuple.of(FeedKey.of("FEED_A", "TYPE_1"), FeedKey.of("FEED_A", "TYPE_2")),
                        false)
                .addCase(
                        Tuple.of(FeedKey.of(null, "TYPE_1"), FeedKey.of("FEED_A", "TYPE_1")),
                        false)
                .addCase(
                        Tuple.of(FeedKey.of("FEED_A", "TYPE_1"), null),
                        false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testCtor() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, String.class)
                .withOutputType(FeedKey.class)
                .withTestFunction(testCase ->
                        FeedKey.of(testCase.getInput()._1(), testCase.getInput()._2()))
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, null), FeedKey.of(null, null))
                .addCase(Tuple.of("", null), FeedKey.of(null, null))
                .addCase(Tuple.of(null, ""), FeedKey.of(null, null))
                .addCase(Tuple.of("", ""), FeedKey.of(null, null))
                .addCase(Tuple.of("foo", null), FeedKey.of("foo", null))
                .addCase(Tuple.of(null, "foo"), FeedKey.of(null, "foo"))
                .addCase(Tuple.of("foo", "bar"), FeedKey.of("foo", "bar"))
                .build();
    }


    @Test
    void testComparable() {
        final List<FeedKey> feedKeys = new ArrayList<>();
        feedKeys.add(FeedKey.of(null, null));
        feedKeys.add(FeedKey.of(null, "TYPE_1"));
        feedKeys.add(FeedKey.of("FEED_A", null));
        feedKeys.add(FeedKey.of("FEED_A", "TYPE_1"));
        feedKeys.add(FeedKey.of("FEED_A", "TYPE_2"));
        feedKeys.add(FeedKey.of("FEED_B", "TYPE_1"));
        feedKeys.add(FeedKey.of("FEED_B", "TYPE_2"));

        final List<FeedKey> sortedFeedKeys = feedKeys.stream()
                .sorted()
                .toList();

        Assertions.assertThat(sortedFeedKeys)
                .containsExactlyElementsOf(feedKeys);
    }
}
