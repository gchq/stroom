package stroom.util.shared;

import stroom.docref.DocRef;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

class TestStringUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestStringUtil.class);

    @Test
    void testToString() {
        DocRef docRef = DocRef.builder()
                .uuid(UUID.randomUUID().toString())
                .type("Index")
                .name("my name")
                .build();

        final String str = StringUtil.toString(docRef);
        Assertions.assertThat(str)
                .isEqualTo(docRef.toString());
    }

    @Test
    void testToString_null() {
        DocRef docRef = null;

        final String str = StringUtil.toString(docRef);
        Assertions.assertThat(str)
                .isEqualTo("");
    }


    @ParameterizedTest
    @CsvSource(nullValues = {"<<NULL>>"}, value = {
            "'abc',false",
            "' abc ',false",
            "'',true",
            "<<NULL>>,true",
            "' ',true",
            "'  ',true",
            "'\n',true",
            "'\t',true",
    })
    void testIsBlank1(final String input, final boolean expectedOutput) {

        final boolean isBlank = StringUtil.isBlank(input);

        LOGGER.info("input: [{}], expectedOutput: {}, isBlank: {}",
                input, expectedOutput, isBlank);

        Assertions.assertThat(isBlank)
                .isEqualTo(expectedOutput);
    }

    @ParameterizedTest
    @CsvSource(nullValues = {"<NULL>"}, value = {
            "<NULL>,<NULL>",
            "'abc','abc'",
            "' abc',' abc'",
            "' ',<NULL>",
            "'  ',<NULL>",
            "'\n',<NULL>",
            "'\t',<NULL>",
    })
    void testBlankAsNull(final String input, final String expectedOutput) {
        final String output = StringUtil.blankAsNull(input);
        Assertions.assertThat(output)
                .isEqualTo(expectedOutput);
    }
}
