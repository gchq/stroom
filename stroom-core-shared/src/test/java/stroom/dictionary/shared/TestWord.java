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

package stroom.dictionary.shared;

import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestWord {

    @Test
    void testEquality() {
        final Word word1a = new Word("cat", "100", null);
        final Word word1b = new Word("cat", "100", null);
        final Word word2a = new Word("cat", "200", null);
        final Word word2b = new Word("dog", "200", null);

        assertThat(word1a)
                .isEqualTo(word1b);
        assertThat(word1a.hashCode())
                .isEqualTo(word1b.hashCode());

        assertThat(word1a)
                .isNotEqualTo(word2a);
        assertThat(word1a.hashCode())
                .isNotEqualTo(word2a.hashCode());

        assertThat(word2a)
                .isNotEqualTo(word2b);
        assertThat(word2a.hashCode())
                .isNotEqualTo(word2b.hashCode());
    }

    @TestFactory
    Stream<DynamicTest> testTrim() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(String.class)
                .withTestFunction(testCase ->
                        new Word(testCase.getInput(), "myUuid", null).getWord())
                .withSimpleEqualityAssertion()
                .addCase("foo", "foo")
                .addCase(" foo", "foo")
                .addCase(" foo ", "foo")
                .addCase("foo ", "foo")
                .addCase("\t foo \t", "foo")
                .addThrowsCase(null, NullPointerException.class)
                .addThrowsCase("", IllegalArgumentException.class)
                .addThrowsCase("\t ", IllegalArgumentException.class)
                .build();
    }

}
