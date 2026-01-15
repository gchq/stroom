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

package stroom.query.client.presenter;

import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class TestTableRow {

    @TestFactory
    Stream<DynamicTest> testGetText() {
        final TableRow tableRow = new TableRow(null,
                null,
                null,
                null,
                null,
                0);

        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withSingleArgTestFunction(tableRow::convertRawCellValue)
                .withSimpleEqualityAssertion()
                .addCase("foo", "foo")
                .addCase("[link1](b){c}", "link1")
                .addCase("[link1](b){c} suffix ", "link1 suffix ")
                .addCase(" prefix [link1](b){c}", " prefix link1")
                .addCase(" prefix [link1](b){c} suffix ", " prefix link1 suffix ")
                .addCase(
                        "[link1](b){c}[link2](e){f}",
                        "link1link2")
                .addCase(
                        " prefix [link1](b){c} and [link2](e){f} suffix ",
                        " prefix link1 and link2 suffix ")
                .addCase(
                        " prefix [link1](b) and [link2](e) suffix ",
                        " prefix link1 and link2 suffix ")
                .addCase(
                        " prefix [link1](b){c} and [link2](e){f} and [link3](g){h} suffix ",
                        " prefix link1 and link2 and link3 suffix ")
                // Malformed links
                .addCase(" prefix link1](b){c} suffix ", " prefix link1](b){c} suffix ")
                .addCase(" prefix [link1]{c} suffix ", " prefix [link1]{c} suffix ")
                // '[link1](b)' is valid on its own so '{' is treated as plain text
                .addCase(" prefix [link1](b){ suffix ", " prefix link1{ suffix ")
                .addCase(" prefix [link1(b){c} suffix ", " prefix [link1(b){c} suffix ")
                .build();
    }
}
