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

import java.util.stream.Stream;

public class TestDecode extends AbstractFunctionTest<Decode> {
    @Override
    Class<Decode> getFunctionType() {
        return Decode.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "match",
                        ValString.create("rgb(0,0,255)"),
                        ValString.create("blue"),
                        ValString.create("^red"),
                        ValString.create("rgb(255,0,0)"),
                        ValString.create("^blue"),
                        ValString.create("rgb(0,0,255)"),
                        ValString.create("rgb(0,255,0)")),
                TestCase.of(
                        "otherwise",
                        ValString.create("rgb(0,255,0)"),
                        ValString.create("green"),
                        ValString.create("^red"),
                        ValString.create("rgb(255,0,0)"),
                        ValString.create("^blue"),
                        ValString.create("rgb(0,0,255)"),
                        ValString.create("rgb(0,255,0)")),
                TestCase.of(
                        "returnsCaptureGroup",
                        ValString.create("blue"),
                        ValString.create("red, white and blue"),
                        ValString.create("^red, (\\w+) and (\\w+)"),
                        ValString.create("$2"),
                        ValString.create("NO MATCH FOUND"))
        );
    }
}
