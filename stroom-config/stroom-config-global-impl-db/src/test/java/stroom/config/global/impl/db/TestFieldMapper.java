package stroom.config.global.impl.db;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestFieldMapper {
    @Test
    void test() {
        MyObject original = new MyObject();
        original.setString("This is a test");

        MyObject copy = new MyObject();
        FieldMapper.copy(original, copy);

        assertThat(copy.getString()).isEqualTo(original.getString());
    }

    private static class MyObject {
        private String string;

        String getString() {
            return string;
        }

        void setString(final String string) {
            this.string = string;
        }
    }
}
