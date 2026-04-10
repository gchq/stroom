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

package stroom.proxy.app.event;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.test.common.TestUtil;
import stroom.util.shared.FeedKey;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.mockito.Mockito;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestFeedKeyEncoder {

    @Test
    @DisplayName("Should encode and decode key consistently (Round-trip)")
    void roundTripTest() {
        final FeedKey original = FeedKey.of("SystemA", "Data_Type_1");

        final String encoded = FeedKeyEncoder.encodeKey(original);
        final FeedKey decoded = FeedKeyEncoder.decodeKey(encoded);

        assertThat(encoded).isEqualTo("SystemA=Data_Type_1");
        assertThat(decoded).isEqualTo(original);
    }

    @TestFactory
    Stream<DynamicTest> testEncoding() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(FeedKey.class)
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    final String encoded = FeedKeyEncoder.encodeKey(testCase.getInput());
                    final FeedKey decoded = FeedKeyEncoder.decodeKey(encoded);

                    // Make sure we can reverse
                    assertThat(decoded)
                            .isEqualTo(testCase.getInput());
                    return encoded;
                })
                .withSimpleEqualityAssertion()
                .addCase(FeedKey.of("Feed 1", "Type 1"), "Feed+1=Type+1")
                .addCase(FeedKey.of("Feed/A", "Type&B"), "Feed%2FA=Type%26B")
                .addCase(FeedKey.of(null, "TypeOnly"), "=TypeOnly")
                .addCase(FeedKey.of("FeedOnly", null), "FeedOnly=")
                .addCase(FeedKey.of(null, null), "=")
                .build();
    }

    @Test
    @DisplayName("Should return empty FeedKey when decoding null or empty string")
    void decodeEmptyInputs() {
        assertThat(FeedKeyEncoder.decodeKey(null))
                .isEqualTo(FeedKey.empty());
        assertThat(FeedKeyEncoder.decodeKey(""))
                .isEqualTo(FeedKey.empty());
    }

    @Test
    @DisplayName("Should create FeedKey from AttributeMap")
    void fromAttributeMap() {
        // Given
        final AttributeMap attributeMap = Mockito.mock(AttributeMap.class);
        Mockito.when(attributeMap.get(StandardHeaderArguments.FEED))
                .thenReturn("FEED_VAL");
        Mockito.when(attributeMap.get(StandardHeaderArguments.TYPE))
                .thenReturn("TYPE_VAL");

        // When
        final FeedKey result = FeedKeyEncoder.from(attributeMap);

        // Then
        assertThat(result.feed())
                .isEqualTo("FEED_VAL");
        assertThat(result.type())
                .isEqualTo("TYPE_VAL");
    }

    @Test
    @DisplayName("Should throw NPE when AttributeMap is null")
    void fromAttributeMapNull() {
        Assertions.assertThatThrownBy(() -> FeedKeyEncoder.from(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should handle malformed or partial strings during decode")
    void decodeMalformedStrings() {
        // Case: No delimiter
        final FeedKey singlePart = FeedKeyEncoder.decodeKey("JustFeedNoDelimiter");
        assertThat(singlePart.feed())
                .isEqualTo("JustFeedNoDelimiter");
        assertThat(singlePart.type())
                .isNull();

        // Case: Extra delimiters (split handles based on first two parts usually)
        final FeedKey extraParts = FeedKeyEncoder.decodeKey("Feed=Type=Extra");
        assertThat(extraParts.feed())
                .isEqualTo("Feed");
        assertThat(extraParts.type())
                .isEqualTo("Type");
    }
}
