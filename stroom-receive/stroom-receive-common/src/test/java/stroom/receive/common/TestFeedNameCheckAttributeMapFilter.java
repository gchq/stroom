/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.meta.shared.DataFormatNames;
import stroom.proxy.StroomStatusCode;
import stroom.receive.common.FeedNameCheckAttributeMapFilter.ConfigState;
import stroom.receive.common.FeedNameCheckAttributeMapFilter.FeedNameGenerator;
import stroom.test.common.TestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestFeedNameCheckAttributeMapFilter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestFeedNameCheckAttributeMapFilter.class);

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
                    final ConfigState state = new ConfigState(true, template);
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
                .addCase("#foo!-${AccountId}-#bar!", "_FOO_-123-_BAR_")
                .addCase("foo-${AccountId}-${compONENT}-bar", "FOO-123-COMPONENT_XYZ-BAR")
                .addCase("foo${AccountId}${compONENT}bar", "FOO123COMPONENT_XYZBAR")
                .addCase("${AccountId}-${compONENT}-${FORMAT}", "123-COMPONENT_XYZ-XML")
                .addCase("${AccountId}-${unknown}-${FORMAT}", "123--XML")
                .build();
    }

    @Test
    void testFeedNameGenDisabled() {
        final AttributeMap attributeMap = new AttributeMap(Map.of(
                StandardHeaderArguments.FEED, "MY_FEED",
                StandardHeaderArguments.ACCOUNT_ID, "123",
                StandardHeaderArguments.COMPONENT, "Component XYZ",
                StandardHeaderArguments.FORMAT, DataFormatNames.XML,
                StandardHeaderArguments.SCHEMA, "events"));

        assertThat(attributeMap.get(StandardHeaderArguments.FEED))
                .isNotNull();

        final ReceiveDataConfig config = ReceiveDataConfig.copy(new ReceiveDataConfig())
                .withFeedNameGenerationEnabled(false)
                .withFeedNameGenerationMandatoryHeaders(Set.of(
                        StandardHeaderArguments.ACCOUNT_ID,
                        StandardHeaderArguments.COMPONENT,
                        StandardHeaderArguments.FORMAT))
                .withFeedNameTemplate("${AccountId}-${component}-${FORMAT}-FOO")
                .build();

        final FeedNameCheckAttributeMapFilter filter = new FeedNameCheckAttributeMapFilter(() -> config);

        final boolean result = filter.filter(attributeMap);
        assertThat(result)
                .isTrue();

        assertThat(attributeMap.get(StandardHeaderArguments.FEED))
                .isEqualTo("MY_FEED");
    }

    @Test
    void testGenerateFeedName() {
        final AttributeMap attributeMap = new AttributeMap(Map.of(
                StandardHeaderArguments.ACCOUNT_ID, "123",
                StandardHeaderArguments.COMPONENT, "Component XYZ",
                StandardHeaderArguments.FORMAT, DataFormatNames.XML,
                StandardHeaderArguments.SCHEMA, "events"));

        assertThat(attributeMap.get(StandardHeaderArguments.FEED))
                .isNull();

        final ReceiveDataConfig config = ReceiveDataConfig.copy(new ReceiveDataConfig())
                .withFeedNameGenerationEnabled(true)
                .withFeedNameGenerationMandatoryHeaders(Set.of(
                        StandardHeaderArguments.ACCOUNT_ID,
                        StandardHeaderArguments.COMPONENT,
                        StandardHeaderArguments.FORMAT))
                .withFeedNameTemplate("${AccountId}-${component}-${FORMAT}-FOO")
                .build();

        final FeedNameCheckAttributeMapFilter filter = new FeedNameCheckAttributeMapFilter(() -> config);

        final boolean result = filter.filter(attributeMap);
        assertThat(result)
                .isTrue();

        assertThat(attributeMap.get(StandardHeaderArguments.FEED))
                .isEqualTo("123-COMPONENT_XYZ-XML-FOO");
    }

    @Test
    void testMissingMandatoryHeader() {
        final AttributeMap attributeMap = new AttributeMap(Map.of(
                StandardHeaderArguments.ACCOUNT_ID, "123",
                StandardHeaderArguments.COMPONENT, "Component XYZ",
                StandardHeaderArguments.SCHEMA, "events"));

        assertThat(attributeMap.get(StandardHeaderArguments.FEED))
                .isNull();

        final ReceiveDataConfig config = ReceiveDataConfig.copy(new ReceiveDataConfig())
                .withFeedNameGenerationEnabled(true)
                .withFeedNameGenerationMandatoryHeaders(Set.of(
                        StandardHeaderArguments.ACCOUNT_ID,
                        StandardHeaderArguments.COMPONENT,
                        StandardHeaderArguments.FORMAT))
                .build();


        final FeedNameCheckAttributeMapFilter filter = new FeedNameCheckAttributeMapFilter(() -> config);

        Assertions.assertThatThrownBy(
                        () -> {
                            filter.filter(attributeMap);
                        })
                .isInstanceOf(StroomStreamException.class)
                .extracting(ex -> (StroomStreamException) ex)
                .extracting(StroomStreamException::getStroomStreamStatus)
                .extracting(StroomStreamStatus::getStroomStatusCode)
                .isEqualTo(StroomStatusCode.MISSING_MANDATORY_HEADER);
    }
}
