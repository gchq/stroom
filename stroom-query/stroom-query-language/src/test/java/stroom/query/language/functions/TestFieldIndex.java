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

package stroom.query.language.functions;

import stroom.util.shared.query.FieldNames;
import stroom.util.shared.string.CIKey;

import org.junit.jupiter.api.Test;

import java.util.Map.Entry;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.util.shared.query.FieldNames.DEFAULT_STREAM_ID_FIELD_NAME;
import static stroom.util.shared.query.FieldNames.DEFAULT_TIME_FIELD_NAME;

class TestFieldIndex {

    @Test
    void test() {
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create("foo");
        fieldIndex.create("FOO"); // same case-insensitive key, so ignored
        fieldIndex.create("BAR");

        assertThat(fieldIndex.getFields())
                .containsExactly("foo", "BAR");

        assertThat(fieldIndex.stream().map(Entry::getKey).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("foo", "BAR");
        assertThat(fieldIndex.getFieldsAsCIKeys())
                .containsExactly(CIKey.of("foo"), CIKey.of("BAR"));

        assertThat(fieldIndex.getPos("xxx"))
                .isEqualTo(null);
        assertThat(fieldIndex.getPos("foo"))
                .isEqualTo(0);
        assertThat(fieldIndex.getPos("bar"))
                .isEqualTo(1);

        assertThat(fieldIndex.size())
                .isEqualTo(2);

        assertThat(fieldIndex.getField(0))
                .isEqualTo("foo");
        assertThat(fieldIndex.getField(1))
                .isEqualTo("BAR");
    }

    @Test
    void test2() {
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create(CIKey.of("foo"));
        fieldIndex.create(CIKey.of("FOO")); // same case-insensitive key, so ignored
        fieldIndex.create(CIKey.of("BAR"));

        assertThat(fieldIndex.getFields())
                .containsExactly("foo", "BAR");

        assertThat(fieldIndex.getFieldsAsCIKeys())
                .containsExactly(CIKey.of("foo"), CIKey.of("BAR"));

        assertThat(fieldIndex.stream().map(Entry::getKey).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("foo", "BAR");

        assertThat(fieldIndex.getPos("xxx"))
                .isEqualTo(null);
        assertThat(fieldIndex.getPos("foo"))
                .isEqualTo(0);
        assertThat(fieldIndex.getPos("bar"))
                .isEqualTo(1);

        assertThat(fieldIndex.size())
                .isEqualTo(2);

        assertThat(fieldIndex.getField(0))
                .isEqualTo("foo");
        assertThat(fieldIndex.getField(1))
                .isEqualTo("BAR");
    }

    @Test
    void getStreamIdFieldIndex() {
        final FieldIndex fieldIndex = new FieldIndex();
        assertThat(fieldIndex.getStreamIdFieldIndex())
                .isEqualTo(fieldIndex.getPos(FieldNames.FALLBACK_STREAM_ID_FIELD_NAME));

        assertThat(fieldIndex.getStreamIdFieldIndex())
                .isEqualTo(fieldIndex.getPos(FieldNames.FALLBACK_STREAM_ID_FIELD_NAME));
    }

    @Test
    void getStreamIdFieldIndex2() {
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create(DEFAULT_STREAM_ID_FIELD_NAME);

        assertThat(fieldIndex.getStreamIdFieldIndex())
                .isEqualTo(fieldIndex.getPos(DEFAULT_STREAM_ID_FIELD_NAME));

        assertThat(fieldIndex.getStreamIdFieldIndex())
                .isEqualTo(fieldIndex.getPos(DEFAULT_STREAM_ID_FIELD_NAME));
    }

    @Test
    void getEventIdFieldIndex() {
        final FieldIndex fieldIndex = new FieldIndex();
        assertThat(fieldIndex.getEventIdFieldIndex())
                .isEqualTo(fieldIndex.getPos(FieldNames.FALLBACK_EVENT_ID_FIELD_NAME));

        assertThat(fieldIndex.getEventIdFieldIndex())
                .isEqualTo(fieldIndex.getPos(FieldNames.FALLBACK_EVENT_ID_FIELD_NAME));
    }

    @Test
    void getEventIdFieldIndex2() {
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create(FieldNames.DEFAULT_EVENT_ID_FIELD_NAME);

        assertThat(fieldIndex.getEventIdFieldIndex())
                .isEqualTo(fieldIndex.getPos(FieldNames.DEFAULT_EVENT_ID_FIELD_NAME));

        assertThat(fieldIndex.getEventIdFieldIndex())
                .isEqualTo(fieldIndex.getPos(FieldNames.DEFAULT_EVENT_ID_FIELD_NAME));
    }

    @Test
    void getTimeFieldIndex() {
        final FieldIndex fieldIndex = new FieldIndex();
        assertThat(fieldIndex.getTimeFieldIndex())
                .isEqualTo(-1);
        assertThat(fieldIndex.getTimeFieldIndex())
                .isEqualTo(-1);
    }

    @Test
    void getTimeFieldIndex2() {
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create(DEFAULT_TIME_FIELD_NAME);

        assertThat(fieldIndex.getTimeFieldIndex())
                .isEqualTo(fieldIndex.getPos(DEFAULT_TIME_FIELD_NAME));

        assertThat(fieldIndex.getTimeFieldIndex())
                .isEqualTo(fieldIndex.getPos(DEFAULT_TIME_FIELD_NAME));
    }
}
