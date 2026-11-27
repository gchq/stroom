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

package stroom.ai.impl.tools.sequence;

import stroom.ai.impl.tools.sequence.JoinKeys.Kind;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests [JoinKeys]: keys compare in the order Elasticsearch sorts them.
class TestJoinKeys {

    @Test
    void textIsInCodePointOrderAsElasticsearchSortsKeywords() {
        // U+1F600 (past U+FFFF) sorts after U+FFFD by code point (and UTF-8 bytes), though not by Java's UTF-16.
        final String emoji = new String(Character.toChars(0x1F600));
        final String replacement = String.valueOf((char) 0xFFFD);

        assertThat(emoji.compareTo(replacement)).isNegative();
        assertThat(JoinKeys.compare(Kind.TEXT, emoji, replacement)).isPositive();
        assertThat(JoinKeys.compare(Kind.TEXT, "B", "a")).isNegative();
        assertThat(JoinKeys.compare(Kind.TEXT, "ab", "abc")).isNegative();
        assertThat(JoinKeys.compare(Kind.TEXT, "abc", "abc")).isZero();
    }

    @Test
    void numbersCompareByValueAndIpsByTheirSixteenBytes() {
        assertThat(JoinKeys.compare(Kind.NUMBER, 9L, 10L)).isNegative();
        assertThat(JoinKeys.compare(Kind.NUMBER, 9.5, 10L)).isNegative();
        assertThat(JoinKeys.compare(Kind.IP, "10.0.0.9", "10.0.0.10")).isNegative();
        assertThat(JoinKeys.compare(Kind.IP, "255.255.255.255", "::1")).isPositive();
        assertThat(JoinKeys.compare(Kind.IP, "2001:db8::1", "255.255.255.255")).isPositive();
        // A name is never looked up: compared as text.
        assertThat(JoinKeys.ipBytes("example.com")).isNull();
        assertThat(JoinKeys.ipBytes("10.0.0.256")).isNull();
        assertThat(JoinKeys.ipBytes("::ffff:10.0.0.1")).isEqualTo(JoinKeys.ipBytes("10.0.0.1"));
        assertThat(JoinKeys.ipBytes("2001:db8:0:0:0:0:0:1")).isEqualTo(JoinKeys.ipBytes("2001:db8::1"));
        assertThat(JoinKeys.ipBytes("1:2:3")).isNull();
        assertThat(JoinKeys.ipBytes("1::2::3")).isNull();
    }

    @Test
    void aKeyComparesFieldByFieldAndAMissingValueFirst() {
        final Comparator<List<Object>> comparator = JoinKeys.comparator(List.of(Kind.TEXT, Kind.NUMBER));
        final List<List<Object>> keys = new ArrayList<>(List.of(List.of("b", 1L), List.of("a", 2L),
                List.of("a", 10L)));
        keys.sort(comparator);

        assertThat(keys).containsExactly(List.of("a", 2L), List.of("a", 10L), List.of("b", 1L));
        assertThat(JoinKeys.compare(Kind.TEXT, null, "a")).isNegative();
    }

    @Test
    void aFieldsTypeSaysHowItCompares() {
        assertThat(JoinKeys.kind("keyword")).isEqualTo(Kind.TEXT);
        assertThat(JoinKeys.kind("long")).isEqualTo(Kind.NUMBER);
        assertThat(JoinKeys.kind("date")).isEqualTo(Kind.NUMBER);
        assertThat(JoinKeys.kind("ip")).isEqualTo(Kind.IP);
        assertThat(JoinKeys.kind("text")).isNull();
        assertThat(JoinKeys.kind("boolean")).isNull();
        assertThat(JoinKeys.text(3.0)).isEqualTo("3");
    }
}
