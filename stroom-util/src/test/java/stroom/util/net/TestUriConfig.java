package stroom.util.net;

import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class TestUriConfig {

    @TestFactory
    Stream<DynamicTest> test() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(MyUriConfig.class)
                .withOutputType(String.class)
                .withTestFunction(testCase -> testCase.getInput().asUri())
                .withSimpleEqualityAssertion()
                .addCase(new MyUriConfig(null, "myhost"), "myhost")
                .addCase(new MyUriConfig("", "myhost"), "myhost")
                .addCase(new MyUriConfig(" ", " myhost "), "myhost")
                .addCase(new MyUriConfig(" http ", " myhost "), "http://myhost")
                .addCase(new MyUriConfig(" http ", " myhost ", 8080),
                        "http://myhost:8080")
                .addCase(new MyUriConfig(" http ", " myhost ", 8080, null),
                        "http://myhost:8080")
                .addCase(new MyUriConfig(" http ", " myhost ", 8080, ""),
                        "http://myhost:8080")
                .addCase(new MyUriConfig(" http ", " myhost ", 8080, " "),
                        "http://myhost:8080")
                .addCase(new MyUriConfig(" http ", " myhost ", 8080, " / "),
                        "http://myhost:8080")
                .addCase(new MyUriConfig(" http ", " myhost ", 8080, " /my/prefix "),
                        "http://myhost:8080/my/prefix")
                .addCase(new MyUriConfig(" http ", " myhost ", null, " /my/prefix "),
                        "http://myhost/my/prefix")
                .build();
    }


    // --------------------------------------------------------------------------------


    private static class MyUriConfig extends UriConfig {

        public MyUriConfig() {
        }

        public MyUriConfig(final String scheme, final String hostname, final Integer port, final String pathPrefix) {
            super(scheme, hostname, port, pathPrefix);
        }

        public MyUriConfig(final String scheme, final String hostname, final Integer port) {
            super(scheme, hostname, port);
        }

        public MyUriConfig(final String scheme, final String hostname) {
            super(scheme, hostname);
        }
    }
}
