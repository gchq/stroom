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

public class TestCase extends AbstractFunctionTest<Case> {
    @Override
    Class<Case> getFunctionType() {
        return Case.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "matchLong",
                        ValLong.create(200),
                        ValLong.create(2),
                        ValLong.create(1),
                        ValLong.create(100),
                        ValLong.create(2),
                        ValLong.create(200),
                        ValString.create("NO MATCH")),
                TestCase.of(
                        "matchString",
                        ValString.create("two"),
                        ValString.create("2"),
                        ValString.create("1"),
                        ValString.create("one"),
                        ValString.create("2"),
                        ValString.create("two"),
                        ValString.create("NO MATCH FOUND")),
                TestCase.of(
                        "matchLongReturnString",
                        ValString.create("two"),
                        ValLong.create(2),
                        ValLong.create(1),
                        ValString.create("one"),
                        ValLong.create(2),
                        ValString.create("two"),
                        ValString.create("NO MATCH FOUND")),
                TestCase.of(
                        "doNotMatchStringToLong",
                        ValString.create("NO MATCH FOUND"),
                        ValString.create("2"),
                        ValLong.create(1),
                        ValString.create("one"),
                        ValLong.create(2),
                        ValString.create("two"),
                        ValString.create("NO MATCH FOUND")),
                TestCase.of(
                        "matchWithChildFunctions",
                        ValBoolean.TRUE,
                        ValLong.create(1),
                        ValLong.create(1),
                        new True("true"),
                        new False("false")),
                TestCase.of(
                        "otherwiseWithChildFunctions",
                        ValNull.INSTANCE,
                        ValLong.create(2),
                        ValLong.create(1),
                        new True("true"),
                        new Null("null")),
                TestCase.of(
                        "otherwiseNoMatchFound",
                        ValString.create("NO MATCH FOUND"),
                        ValLong.create(4),
                        ValLong.create(1),
                        ValString.create("one"),
                        ValLong.create(2),
                        ValString.create("two"),
                        ValString.create("NO MATCH FOUND"))
        );
    }
}
