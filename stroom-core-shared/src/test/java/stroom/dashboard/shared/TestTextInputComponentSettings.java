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

package stroom.dashboard.shared;

import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestTextInputComponentSettings {

    @Test
    void testSerialisation() {
        final TextInputComponentSettings settings = TextInputComponentSettings.builder()
                .key("myKey")
                .value("ABC-123")
                .format(TextInputFormat.PATTERN)
                .pattern("[A-Z]{3}-\\d+")
                .build();

        final String json = JsonUtil.writeValueAsString(settings);
        final TextInputComponentSettings deserialised = JsonUtil.readValue(json, TextInputComponentSettings.class);

        assertThat(deserialised).isEqualTo(settings);
        assertThat(deserialised.getFormat()).isEqualTo(TextInputFormat.PATTERN);
        assertThat(deserialised.getPattern()).isEqualTo("[A-Z]{3}-\\d+");
    }

    /// Settings saved before formats existed have no format, so behave as plain text.
    @Test
    void testDeserialisation_noFormat() {
        final String json = """
                {"type":"text-input","key":"myKey","value":"abc"}""";

        final TextInputComponentSettings deserialised = JsonUtil.readValue(json, TextInputComponentSettings.class);

        assertThat(deserialised.getKey()).isEqualTo("myKey");
        assertThat(deserialised.getValue()).isEqualTo("abc");
        assertThat(deserialised.getFormat()).isEqualTo(TextInputFormat.TEXT);
        assertThat(deserialised.getPattern()).isNull();
    }

    @Test
    void testBuilder_nullFormatIsText() {
        final TextInputComponentSettings settings = TextInputComponentSettings.builder()
                .format(null)
                .build();

        assertThat(settings.getFormat()).isEqualTo(TextInputFormat.TEXT);
        assertThat(settings).isEqualTo(TextInputComponentSettings.builder().build());
    }

    @Test
    void testCopy() {
        final TextInputComponentSettings settings = TextInputComponentSettings.builder()
                .key("myKey")
                .format(TextInputFormat.DATE_TIME)
                .build();

        final TextInputComponentSettings copy = settings.copy().value("now()-1d").build();

        assertThat(copy.getKey()).isEqualTo("myKey");
        assertThat(copy.getFormat()).isEqualTo(TextInputFormat.DATE_TIME);
        assertThat(copy.getValue()).isEqualTo("now()-1d");
    }

    @Test
    void testEquals_formatAndPatternMatter() {
        final TextInputComponentSettings settings = TextInputComponentSettings.builder()
                .format(TextInputFormat.PATTERN)
                .pattern("a")
                .build();

        assertThat(settings.copy().pattern("b").build()).isNotEqualTo(settings);
        assertThat(settings.copy().format(TextInputFormat.NUMBER).build()).isNotEqualTo(settings);
        assertThat(settings.copy().build()).isEqualTo(settings).hasSameHashCodeAs(settings);
    }
}
