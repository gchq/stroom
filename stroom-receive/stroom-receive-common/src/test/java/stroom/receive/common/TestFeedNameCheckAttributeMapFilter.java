package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.meta.shared.DataFormatNames;
import stroom.receive.common.FeedNameCheckAttributeMapFilter.ConfigState;
import stroom.receive.common.FeedNameCheckAttributeMapFilter.FeedNameGenerator;
import stroom.test.common.TestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Map;
import java.util.stream.Stream;

class TestFeedNameCheckAttributeMapFilter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestFeedNameCheckAttributeMapFilter.class);

//    @Test
//    void name() {
//        final AttributeMap attributeMap = new AttributeMap(Map.of(
//                StandardHeaderArguments.ACCOUNT_ID, "123456789",
//                StandardHeaderArguments.COMPONENT, "back end",
//                StandardHeaderArguments.FORMAT, "XML",
//                StandardHeaderArguments.SCHEMA, "event-logging",
//                StandardHeaderArguments.SCHEMA_VERSION, "4.0.1",
//                StandardHeaderArguments.TYPE, StreamTypeNames.EVENTS));
//
//        final String name = FeedNameCheckAttributeMapFilter.deriveFeedName(
//                attributeMap, attributeMap.get(StandardHeaderArguments.TYPE));
//
//        LOGGER.debug("name: {}", name);
//
//        assertThat(name)
//                .isEqualTo("123456789-BACK_END-EVENT_LOGGING-XML-EVENTS");
//    }

    @TestFactory
    Stream<DynamicTest> testFeedNameGenerator() {
        final AttributeMap attributeMap = new AttributeMap(Map.of(
                StandardHeaderArguments.ACCOUNT_ID, "123",
                StandardHeaderArguments.COMPONENT, "Component XYZ",
                StandardHeaderArguments.FORMAT, DataFormatNames.XML,
                StandardHeaderArguments.SCHEMA, "events"));

        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withTestFunction(testCase -> {
                    final String template = testCase.getInput();
                    final ConfigState state = new ConfigState(
                            true,
                            template,
                            null);
                    final FeedNameGenerator feedNameGenerator = new FeedNameGenerator(state);
                    return feedNameGenerator.generateName(attributeMap);
                })
                .withSimpleEqualityAssertion()
                .addCase(null, "")
                .addCase("", "")
                .addCase("--", "--")
                .addCase("--foo--", "--FOO--")
                .addCase("foo bar", "FOO_BAR")
                .addCase("${AccountId}", "123")
                .addCase("${component}", "COMPONENT_XYZ")
                .addCase("${unknown}", "")
                .addCase("foo-${AccountId}-bar", "FOO-123-BAR")
                .addCase("foo-${AccountId}-${compONENT}-bar", "FOO-123-COMPONENT_XYZ-BAR")
                .addCase("foo${AccountId}${compONENT}bar", "FOO123COMPONENT_XYZBAR")
                .addCase("${AccountId}-${compONENT}-${FORMAT}", "123-COMPONENT_XYZ-XML")
                .addCase("${AccountId}-${unknown}-${FORMAT}", "123--XML")
                .build();
    }
}
