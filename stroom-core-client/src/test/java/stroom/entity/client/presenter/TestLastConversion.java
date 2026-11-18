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

package stroom.entity.client.presenter;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestLastConversion {

    @Test
    void testSameTextConvertedOnce() {
        final LastConversion lastConversion = new LastConversion();
        final List<String> converted = new ArrayList<>();
        final Function<String, String> converter = recording(converted);

        assertThat(lastConversion.convert("# Heading", converter)).isEqualTo("<h1>Heading</h1>");
        // An equal text, not the same object, as a document's text is when read again.
        assertThat(lastConversion.convert(new String("# Heading"), converter)).isEqualTo("<h1>Heading</h1>");

        assertThat(converted).containsExactly("# Heading");
    }

    @Test
    void testChangedTextConvertedAgain() {
        final LastConversion lastConversion = new LastConversion();
        final List<String> converted = new ArrayList<>();
        final Function<String, String> converter = recording(converted);

        assertThat(lastConversion.convert("a", converter)).isEqualTo("<h1>a</h1>");
        assertThat(lastConversion.convert("b", converter)).isEqualTo("<h1>b</h1>");
        // Only the last text is kept.
        assertThat(lastConversion.convert("a", converter)).isEqualTo("<h1>a</h1>");

        assertThat(converted).containsExactly("a", "b", "a");
    }

    @Test
    void testNullText() {
        final LastConversion lastConversion = new LastConversion();
        final List<String> converted = new ArrayList<>();
        final Function<String, String> converter = text -> {
            converted.add(String.valueOf(text));
            return "";
        };

        assertThat(lastConversion.convert(null, converter)).isEmpty();
        assertThat(lastConversion.convert(null, converter)).isEmpty();
        assertThat(lastConversion.convert("", converter)).isEmpty();

        assertThat(converted).containsExactly("null", "");
    }

    @Test
    void testNothingConvertedIsNotKept() {
        final LastConversion lastConversion = new LastConversion();
        final List<String> converted = new ArrayList<>();
        final Function<String, String> converter = text -> {
            converted.add(text);
            return null;
        };

        assertThat(lastConversion.convert("a", converter)).isNull();
        assertThat(lastConversion.convert("a", converter)).isNull();

        // A failed conversion (null) is tried again.
        assertThat(converted).containsExactly("a", "a");
    }

    @Test
    void testNoConverter() {
        final LastConversion lastConversion = new LastConversion();

        assertThatThrownBy(() -> lastConversion.convert("a", null))
                .isInstanceOf(NullPointerException.class);
    }

    private static Function<String, String> recording(final List<String> converted) {
        return text -> {
            converted.add(text);
            return "<h1>" + text.replace("# ", "") + "</h1>";
        };
    }
}
