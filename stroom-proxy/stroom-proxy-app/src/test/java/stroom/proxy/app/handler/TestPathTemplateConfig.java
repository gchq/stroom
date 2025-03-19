package stroom.proxy.app.handler;

import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class TestPathTemplateConfig {

    @TestFactory
    Stream<DynamicTest> testHasPathTemplate() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(PathTemplateConfig.class)
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(PathTemplateConfig::hasPathTemplate)
                .withSimpleEqualityAssertion()
                .addCase(
                        new PathTemplateConfig(true, "", TemplatingMode.IGNORE_UNKNOWN_PARAMS),
                        false)
                .addCase(
                        new PathTemplateConfig(true, " ", TemplatingMode.IGNORE_UNKNOWN_PARAMS),
                        false)
                .addCase(
                        new PathTemplateConfig(
                                false, "${foo}", TemplatingMode.IGNORE_UNKNOWN_PARAMS),
                        false)
                .addCase(
                        new PathTemplateConfig(
                                true, "${foo}", TemplatingMode.IGNORE_UNKNOWN_PARAMS),
                        true)
                .addCase(
                        new PathTemplateConfig(true, null, TemplatingMode.IGNORE_UNKNOWN_PARAMS),
                        true)
                .build();
    }
}
