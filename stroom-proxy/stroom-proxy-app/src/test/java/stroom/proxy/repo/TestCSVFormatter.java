/*
 * Copyright 2025 Crown Copyright
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

package stroom.proxy.repo;

import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class TestCSVFormatter {

    @TestFactory
    Stream<DynamicTest> testEscape() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withSingleArgTestFunction(CSVFormatter::escape)
                .withSimpleEqualityAssertion()
                .addCase(null, "")
                .addCase("", "")
                .addCase("\"", "\"\"")
                .addCase("foo", "foo")
                // Was ("foo,bar", "foo,bar") — it pinned the defect. LogStream joins the
                // top-level fields of every receive-log line with commas and does not quote them, so
                // an unescaped comma in a URL, a receipt id or an error message shifted every column
                // after it. A log that changes shape when the data contains a comma is worse than no
                // log, because it still parses.
                .addCase("foo,bar", "foo\\,bar")
                .addCase("a=b,c\"d", "a\\=b\\,c\"\"d")
                .build();
    }
}
