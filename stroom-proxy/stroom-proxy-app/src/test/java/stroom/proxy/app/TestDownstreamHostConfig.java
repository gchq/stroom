package stroom.proxy.app;

import stroom.test.common.TestUtil;

import io.vavr.Tuple;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class TestDownstreamHostConfig {

    @TestFactory
    Stream<DynamicTest> testCreateUri1() {
        final DownstreamHostConfig downstreamHostConfig = DownstreamHostConfig.builder()
                .withScheme("https")
                .withHostname("stroomhost")
                .withPort(8443)
                .withPrefix("/prefix/")
                .build();
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(String.class)
                .withTestFunction(testCase ->
                        downstreamHostConfig.createUri(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, "https://stroomhost:8443/prefix/")
                .addCase("", "https://stroomhost:8443/prefix/")
                .addCase(" ", "https://stroomhost:8443/prefix/")
                .addCase("/foo/bar", "https://stroomhost:8443/prefix/foo/bar")
                .addCase(
                        " /foo/bar ",
                        "https://stroomhost:8443/prefix/foo/bar")
                .addCase(
                        "foo/bar ",
                        "https://stroomhost:8443/prefix/foo/bar")
                .addCase(
                        "/",
                        "https://stroomhost:8443/prefix/")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testCreateUri2() {
        final DownstreamHostConfig downstreamHostConfig = DownstreamHostConfig.builder()
                .withScheme("https")
                .withHostname("stroomhost")
                .withPort(8443)
                .withPrefix("/prefix/")
                .build();
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, String.class)
                .withOutputType(String.class)
                .withTestFunction(testCase ->
                        downstreamHostConfig.createUri(
                                testCase.getInput()._1(),
                                testCase.getInput()._2()))
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, "/default/path"), "https://stroomhost:8443/prefix/default/path")
                .addCase(Tuple.of("", "/default/path"), "https://stroomhost:8443/prefix/default/path")
                .addCase(Tuple.of(" ", "/default/path"), "https://stroomhost:8443/prefix/default/path")
                .addCase(Tuple.of("/foo/bar", "/default/path"), "https://stroomhost:8443/prefix/foo/bar")
                .addCase(
                        Tuple.of(" /foo/bar ", "/default/path"),
                        "https://stroomhost:8443/prefix/foo/bar")
                .addCase(
                        Tuple.of("foo/bar ", "/default/path"),
                        "foo/bar")
                .addCase(
                        Tuple.of("http://localhost/foo/bar", "/default/path"),
                        "http://localhost/foo/bar")
                .addCase(
                        Tuple.of(" http://localhost/foo/bar ", "/default/path"),
                        "http://localhost/foo/bar")
                .build();
    }
}
