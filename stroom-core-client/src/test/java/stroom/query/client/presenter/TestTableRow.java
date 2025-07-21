package stroom.query.client.presenter;

import stroom.hyperlink.client.Hyperlink.UrlDecoder;
import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class TestTableRow {

    @TestFactory
    Stream<DynamicTest> testGetText() {
        // Use a mock UrlDecoder to stop it using URL::decodeQueryString which is GWT client-side
        // code
        final UrlDecoder mockUrlDecoder = str -> str;
        final TableRow tableRow = new TableRow(null,
                null,
                null,
                null,
                null,
                mockUrlDecoder);

        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withSingleArgTestFunction(tableRow::convertRawCellValue)
                .withSimpleEqualityAssertion()
                .addCase("foo", "foo")
                .addCase("[link1](b){c}", "link1")
                .addCase("[link1](b){c} suffix ", "link1 suffix ")
                .addCase(" prefix [link1](b){c}", " prefix link1")
                .addCase(" prefix [link1](b){c} suffix ", " prefix link1 suffix ")
                .addCase(
                        "[link1](b){c}[link2](e){f}",
                        "link1link2")
                .addCase(
                        " prefix [link1](b){c} and [link2](e){f} suffix ",
                        " prefix link1 and link2 suffix ")
                .addCase(
                        " prefix [link1](b) and [link2](e) suffix ",
                        " prefix link1 and link2 suffix ")
                .addCase(
                        " prefix [link1](b){c} and [link2](e){f} and [link3](g){h} suffix ",
                        " prefix link1 and link2 and link3 suffix ")
                // Malformed links
                .addCase(" prefix link1](b){c} suffix ", " prefix link1](b){c} suffix ")
                .addCase(" prefix [link1]{c} suffix ", " prefix [link1]{c} suffix ")
                // '[link1](b)' is valid on its own so '{' is treated as plain text
                .addCase(" prefix [link1](b){ suffix ", " prefix link1{ suffix ")
                .addCase(" prefix [link1(b){c} suffix ", " prefix [link1(b){c} suffix ")
                .build();
    }
}
