package stroom.explorer.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestTagsPatternParser {

    @Test
    void test() {
        final String pattern = "tag:myTag this is some tag:anotherTag text";

        final TagsPatternParser patternParser = new TagsPatternParser(pattern);

        assertThat(patternParser.getText()).isEqualTo("this is some text");
        assertThat(patternParser.getTags()).containsExactlyInAnyOrder("myTag", "anotherTag");
    }

}
