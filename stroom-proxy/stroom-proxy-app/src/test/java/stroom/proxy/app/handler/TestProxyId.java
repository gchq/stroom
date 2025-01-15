package stroom.proxy.app.handler;

import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class TestProxyId {

    @TestFactory
    Stream<DynamicTest> test() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withSingleArgTestFunction(ProxyId::createSafeString)
                .withSimpleEqualityAssertion()
                .addCase("", "")
                .addCase("foo_bar", "foo-bar")
                .addCase("foo-bar", "foo-bar")
                .addCase("foo-bar?()", "foo-bar---")
                .build();
    }
}
