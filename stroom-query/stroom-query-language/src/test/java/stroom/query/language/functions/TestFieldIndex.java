package stroom.query.language.functions;

import org.junit.jupiter.api.Test;

import java.util.Map.Entry;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.query.language.functions.FieldIndex.DEFAULT_EVENT_ID_FIELD_NAME;
import static stroom.query.language.functions.FieldIndex.DEFAULT_STREAM_ID_FIELD_NAME;
import static stroom.query.language.functions.FieldIndex.FALLBACK_EVENT_ID_FIELD_NAME;
import static stroom.query.language.functions.FieldIndex.FALLBACK_STREAM_ID_FIELD_NAME;

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
                .isEqualTo(fieldIndex.getPos(FieldIndex.FALLBACK_STREAM_ID_FIELD_NAME));

        assertThat(fieldIndex.getStreamIdFieldIndex())
                .isEqualTo(fieldIndex.getPos(FieldIndex.FALLBACK_STREAM_ID_FIELD_NAME));
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
                .isEqualTo(fieldIndex.getPos(FieldIndex.FALLBACK_EVENT_ID_FIELD_NAME));

        assertThat(fieldIndex.getEventIdFieldIndex())
                .isEqualTo(fieldIndex.getPos(FieldIndex.FALLBACK_EVENT_ID_FIELD_NAME));
    }

    @Test
    void getEventIdFieldIndex2() {
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create(FieldIndex.DEFAULT_EVENT_ID_FIELD_NAME);

        assertThat(fieldIndex.getEventIdFieldIndex())
                .isEqualTo(fieldIndex.getPos(FieldIndex.DEFAULT_EVENT_ID_FIELD_NAME));

        assertThat(fieldIndex.getEventIdFieldIndex())
                .isEqualTo(fieldIndex.getPos(FieldIndex.DEFAULT_EVENT_ID_FIELD_NAME));
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
        fieldIndex.create(FieldIndex.DEFAULT_TIME_FIELD_NAME);

        assertThat(fieldIndex.getTimeFieldIndex())
                .isEqualTo(fieldIndex.getPos(FieldIndex.DEFAULT_TIME_FIELD_NAME));

        assertThat(fieldIndex.getTimeFieldIndex())
                .isEqualTo(fieldIndex.getPos(FieldIndex.DEFAULT_TIME_FIELD_NAME));
    }

    @Test
    void isStreamIdFieldName() {
        assertThat(FieldIndex.isStreamIdFieldName("foo"))
                .isFalse();
        assertThat(FieldIndex.isStreamIdFieldName(DEFAULT_STREAM_ID_FIELD_NAME))
                .isTrue();
        assertThat(FieldIndex.isStreamIdFieldName(FALLBACK_STREAM_ID_FIELD_NAME))
                .isTrue();
    }

    @Test
    void isEventIdFieldName() {
        assertThat(FieldIndex.isEventIdFieldName("foo"))
                .isFalse();
        assertThat(FieldIndex.isEventIdFieldName(DEFAULT_EVENT_ID_FIELD_NAME))
                .isTrue();
        assertThat(FieldIndex.isEventIdFieldName(FALLBACK_EVENT_ID_FIELD_NAME))
                .isTrue();
    }
}
