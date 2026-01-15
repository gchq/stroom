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

package stroom.query.language.functions;

import org.junit.jupiter.api.Disabled;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.stream.Stream;

@Disabled // inconsistent error returned in different environments.
class TestHostAddress extends AbstractFunctionTest<HostAddress> {

    @Override
    Class<HostAddress> getFunctionType() {
        return HostAddress.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of("localhost", "127.0.0.1", "localhost"),
                TestCase.of("ip address", "127.0.0.1", "127.0.0.1"),
                TestCase.of("public domain", resolveHostAddress("github.com"), "github.com"),
                TestCase.of("public ip", "8.8.8.8", "8.8.8.8"),
                TestCase.of("unknown host",
                        ValErr.create("a.b.c.d.invalid.host: Name or service not known"),
                        ValString.create("a.b.c.d.invalid.host"))
        );
    }

    private static String resolveHostAddress(final String host) {
        try {
            return InetAddress.getByName(host).getHostAddress();
        } catch (final UnknownHostException e) {
            return "";
        }
    }
}
