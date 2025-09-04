package stroom.annotation.shared;

import stroom.test.common.TestUtil;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestStringEntryValue {

    @Test
    void testSerDeser() {
        TestUtil.testSerialisation(StringEntryValue.of("foo"), StringEntryValue.class);
    }

    @Test
    void getAsUiValue() {
        final StringEntryValue value = StringEntryValue.of("foo");

        assertThat(value.asUiValue())
                .isEqualTo("foo");
    }
}
