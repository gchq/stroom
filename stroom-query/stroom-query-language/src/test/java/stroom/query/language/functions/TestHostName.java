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

import java.util.stream.Stream;

@Disabled // inconsistent error returned in different environments.
class TestHostName extends AbstractFunctionTest<HostName> {

    @Override
    Class<HostName> getFunctionType() {
        return HostName.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of("localhost", "localhost", "localhost"),
                TestCase.of("public domain", "google.com", "google.com"),
                TestCase.of("ip address", "dns.google", "8.8.8.8"),
                TestCase.of("unknown host", ValErr.create("a.b.c.d.invalid.host"),
                        ValString.create("a.b.c.d.invalid.host"))
        );
    }
}
