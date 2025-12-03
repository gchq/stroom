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
