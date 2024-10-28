/*
 * Copyright 2024 Crown Copyright
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

package stroom.util.shared.query;

import stroom.test.common.TestUtil;
import stroom.util.shared.string.CIKey;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.util.shared.query.FieldNames.DEFAULT_EVENT_ID_FIELD_NAME;
import static stroom.util.shared.query.FieldNames.DEFAULT_STREAM_ID_FIELD_NAME;
import static stroom.util.shared.query.FieldNames.DEFAULT_TIME_FIELD_NAME;
import static stroom.util.shared.query.FieldNames.FALLBACK_EVENT_ID_FIELD_NAME;
import static stroom.util.shared.query.FieldNames.FALLBACK_STREAM_ID_FIELD_NAME;
import static stroom.util.shared.query.FieldNames.FALLBACK_TIME_FIELD_NAME;

class TestFieldNames {

    @Test
    void isStreamIdFieldName() {
        assertThat(FieldNames.isStreamIdFieldName("foo"))
                .isFalse();
        assertThat(FieldNames.isStreamIdFieldName(DEFAULT_STREAM_ID_FIELD_NAME))
                .isTrue();
        assertThat(FieldNames.isStreamIdFieldName(FALLBACK_STREAM_ID_FIELD_NAME))
                .isTrue();
    }

    @Test
    void isEventIdFieldName() {
        assertThat(FieldNames.isEventIdFieldName("foo"))
                .isFalse();
        assertThat(FieldNames.isEventIdFieldName(DEFAULT_EVENT_ID_FIELD_NAME))
                .isTrue();
        assertThat(FieldNames.isEventIdFieldName(FALLBACK_EVENT_ID_FIELD_NAME))
                .isTrue();
    }

    @TestFactory
    Stream<DynamicTest> testCreateCIKey() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(CIKey.class)
                .withSingleArgTestFunction(CIKey::of)
                .withAssertions(outcome -> {
                    final String input = outcome.getInput();
                    final CIKey actualOutput = outcome.getActualOutput();
                    assertThat(actualOutput.get())
                            .isEqualTo(input);
                    assertThat(actualOutput)
                            .isSameAs(outcome.getExpectedOutput());
                })
                // Not using expected output
                .addCase(DEFAULT_TIME_FIELD_NAME, FieldNames.DEFAULT_TIME_FIELD_KEY)
                .addCase(FALLBACK_TIME_FIELD_NAME, FieldNames.FALLBACK_TIME_FIELD_KEY)
                .addCase(DEFAULT_STREAM_ID_FIELD_NAME, FieldNames.DEFAULT_STREAM_ID_FIELD_KEY)
                .addCase(FALLBACK_STREAM_ID_FIELD_NAME, FieldNames.FALLBACK_STREAM_ID_FIELD_KEY)
                .addCase(DEFAULT_EVENT_ID_FIELD_NAME, FieldNames.DEFAULT_EVENT_ID_FIELD_KEY)
                .addCase(FALLBACK_EVENT_ID_FIELD_NAME, FieldNames.FALLBACK_EVENT_ID_FIELD_KEY)
                .addCase(FALLBACK_EVENT_ID_FIELD_NAME, FieldNames.FALLBACK_EVENT_ID_FIELD_KEY)
                .addCase(null, CIKey.NULL_STRING)
                .addCase("", CIKey.EMPTY_STRING)
                .build();
    }
}
