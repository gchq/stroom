package stroom.util.shared;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestTextRange {

    @Test
    void testIsInsideRange() {

        final TextRange highlight = new TextRange(
                DefaultLocation.of(1, 10),
                DefaultLocation.of(1, 30));

        // Exactly the same as the range
        final boolean result = highlight.isInsideRange(
                DefaultLocation.of(1, 10),
                DefaultLocation.of(1, 30));

        Assertions.assertThat(result)
                .isTrue();
    }

    @Test
    void testIsInsideRange2() {

        final TextRange highlight = new TextRange(
                DefaultLocation.of(1, 10),
                DefaultLocation.of(1, 30));

        final boolean result = highlight.isInsideRange(
                DefaultLocation.of(1, 10),
                DefaultLocation.of(1, 29));

        Assertions.assertThat(result)
                .isFalse();
    }

    @Test
    void testIsInsideRange3() {

        final TextRange highlight = new TextRange(
                DefaultLocation.of(1, 10),
                DefaultLocation.of(1, 30));

        final boolean result = highlight.isInsideRange(
                DefaultLocation.of(1, 11),
                DefaultLocation.of(1, 30));

        Assertions.assertThat(result)
                .isFalse();
    }

    @Test
    void testIsInsideRange4() {

        final TextRange highlight = new TextRange(
                DefaultLocation.of(1, 15),
                DefaultLocation.of(1, 25));

        final boolean result = highlight.isInsideRange(
                DefaultLocation.of(1, 1),
                DefaultLocation.of(1, 2000));

        Assertions.assertThat(result)
                .isTrue();
    }

    @Test
    void testIsInsideRange5() {

        final TextRange highlight = new TextRange(
                DefaultLocation.of(1, 10),
                DefaultLocation.of(1, 30));

        final boolean result = highlight.isInsideRange(
                DefaultLocation.of(1, 12),
                DefaultLocation.of(1, 28));

        Assertions.assertThat(result)
                .isFalse();
    }
}