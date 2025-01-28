package stroom.receive.common;

import stroom.data.shared.StreamTypeNames;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestFeedNameCheckAttributeMapFilter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestFeedNameCheckAttributeMapFilter.class);

    @Test
    void name() {
        final AttributeMap attributeMap = new AttributeMap(Map.of(
                StandardHeaderArguments.ACCOUNT_ID, "123456789",
                StandardHeaderArguments.COMPONENT, "back end",
                StandardHeaderArguments.FORMAT, "XML",
                StandardHeaderArguments.SCHEMA, "event-logging",
                StandardHeaderArguments.SCHEMA_VERSION, "4.0.1",
                StandardHeaderArguments.TYPE, StreamTypeNames.EVENTS));

        final String name = FeedNameCheckAttributeMapFilter.deriveFeedName(
                attributeMap, attributeMap.get(StandardHeaderArguments.TYPE));

        LOGGER.debug("name: {}", name);

        assertThat(name)
                .isEqualTo("123456789-BACK_END-EVENT_LOGGING-XML-EVENTS");
    }
}
