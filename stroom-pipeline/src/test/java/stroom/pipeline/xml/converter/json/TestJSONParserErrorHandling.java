/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.pipeline.xml.converter.json;

import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestJSONParserErrorHandling {

    // The object is never closed so the parse fails at the end of the input, i.e. line 3.
    private static final String UNCLOSED_OBJECT_JSON = """
            {
              "foo": "bar"
            """;

    /**
     * Covers the source descriptions that jackson embeds in its messages, which
     * {@link JSONParser#tidyMessage(String)} has to strip out. See gh-5738.
     */
    @TestFactory
    Stream<DynamicTest> tidyMessage() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(String.class)
                .withSingleArgTestFunction(JSONParser::tidyMessage)
                .withSimpleEqualityAssertion()
                .addCase(null, null)
                .addCase("", "")
                .addCase("Nothing to strip here", "Nothing to strip here")
                // The form used when StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION is enabled
                .addCase("Boom (start marker at [Source: (StringReader); line: 1, column: 1])",
                        "Boom (start marker at [line: 1, column: 1])")
                // The form used by jackson v3 when the source is redacted, which is the default
                .addCase("Boom (start marker at [Source: REDACTED (`StreamReadFeature"
                         + ".INCLUDE_SOURCE_IN_LOCATION` disabled); byte offset: #UNKNOWN])",
                        "Boom (start marker at [byte offset: #UNKNOWN])")
                .addCase("Boom [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); "
                         + "char offset: 12]",
                        "Boom [char offset: 12]")
                .build();
    }

    /**
     * The messages asserted on here come from jackson, so this will fail if a jackson upgrade
     * changes the format that {@link JSONParser#tidyMessage(String)} matches on.
     */
    @Test
    void parse_invalidJson() throws IOException, SAXException {
        final RecordingErrorHandler errorHandler = new RecordingErrorHandler();
        parse(UNCLOSED_OBJECT_JSON, errorHandler);

        assertThat(errorHandler.fatalErrors)
                .hasSize(1);
        final SAXParseException error = errorHandler.fatalErrors.getFirst();
        assertThat(error.getMessage())
                .containsIgnoringCase("unexpected end-of-input")
                .doesNotContainIgnoringCase("Source:");
        // The location is reported via the locator rather than in the message.
        assertThat(error.getLineNumber())
                .isEqualTo(3);
        assertThat(error.getColumnNumber())
                .isEqualTo(1);
    }

    @Test
    void parse_invalidJson_noErrorHandler() {
        // With no error handler to report to, the caller must still be told what is wrong with
        // the JSON rather than getting an NPE. See gh-5738.
        assertThatThrownBy(() -> parse(UNCLOSED_OBJECT_JSON, null))
                .isInstanceOf(SAXParseException.class)
                .hasMessageContaining("Unexpected end-of-input");
    }

    @Test
    void parse_truncationWarning() throws IOException, SAXException {
        final RecordingErrorHandler errorHandler = new RecordingErrorHandler();
        parse("{\"foo\": \"barbarbar\"}", errorHandler, 3);

        assertThat(errorHandler.warnings)
                .hasSize(1);
        assertThat(errorHandler.warnings.getFirst().getMessage())
                .containsIgnoringCase("truncated");
        assertThat(errorHandler.fatalErrors)
                .isEmpty();
    }

    @Test
    void parse_truncationWarning_noErrorHandler() {
        // A truncation warning is not fatal, so with no error handler to report it to the parse
        // must still succeed rather than throwing.
        assertThatNoException()
                .isThrownBy(() -> parse("{\"foo\": \"barbarbar\"}", null, 3));
    }

    private void parse(final String json,
                       final ErrorHandler errorHandler) throws IOException, SAXException {
        parse(json, errorHandler, JSONFactoryConfig.DEFAULT_STRING_TRUNCATE_LENGTH);
    }

    private void parse(final String json,
                       final ErrorHandler errorHandler,
                       final int stringTruncateLength) throws IOException, SAXException {
        final JSONFactoryConfig config = JSONFactoryConfig.builder()
                .stringTruncateLength(stringTruncateLength)
                .build();
        final JSONParser parser = new JSONParser(config, false);
        parser.setContentHandler(new DefaultHandler());
        if (errorHandler != null) {
            parser.setErrorHandler(errorHandler);
        }
        parser.parse(new InputSource(new StringReader(json)));
    }


    // --------------------------------------------------------------------------------


    private static class RecordingErrorHandler implements ErrorHandler {

        private final List<SAXParseException> warnings = new ArrayList<>();
        private final List<SAXParseException> errors = new ArrayList<>();
        private final List<SAXParseException> fatalErrors = new ArrayList<>();

        @Override
        public void warning(final SAXParseException exception) {
            warnings.add(exception);
        }

        @Override
        public void error(final SAXParseException exception) {
            errors.add(exception);
        }

        @Override
        public void fatalError(final SAXParseException exception) {
            fatalErrors.add(exception);
        }
    }
}
