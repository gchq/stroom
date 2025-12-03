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

package stroom.query.impl;

import stroom.query.shared.QueryHelpFunctionSignature.Arg;
import stroom.query.shared.QueryHelpFunctionSignature.Type;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestFunctionSignatureUtil {

    @Test
    void testNoArgs() {
        final String str = FunctionSignatureUtil.buildSignatureStr("func", Collections.emptyList());
        assertThat(str)
                .isEqualTo("func ()");
    }

    @Test
    void testNullArgs() {
        final String str = FunctionSignatureUtil.buildSignatureStr("func", null);
        assertThat(str)
                .isEqualTo("func ()");
    }

    @Test
    void testOneArg() {
        final String str = FunctionSignatureUtil.buildSignatureStr("func", simpleArgs("foo"));
        assertThat(str)
                .isEqualTo("func (foo)");
    }

    @Test
    void testTwoArgs() {
        final String str = FunctionSignatureUtil.buildSignatureStr("func", simpleArgs("foo", "bar"));
        assertThat(str)
                .isEqualTo("func (foo, bar)");
    }

    @Test
    void testFourArgs() {
        final String str = FunctionSignatureUtil.buildSignatureStr("func",
                simpleArgs("apple", "pear", "orange", "banana"));
        assertThat(str)
                .isEqualTo("func (app, pea, ora, ban)");
    }

    @Test
    void testOptional() {
        final String str = FunctionSignatureUtil.buildSignatureStr("func",
                combine(
                        simpleArgs("apple", "pear"),
                        optionalArgs("orange", "banana")));
        assertThat(str)
                .isEqualTo("func (app, pea, [ora, ban])");
    }

    @Test
    void testVarags() {
        final String str = FunctionSignatureUtil.buildSignatureStr("func",
                combine(
                        simpleArgs("apple", "pear"),
                        Collections.singletonList(new Arg(
                                "val",
                                Type.STRING,
                                false,
                                true,
                                2,
                                "my desc",
                                null,
                                null))));
        assertThat(str)
                .isEqualTo("func (app, pea, val1, val2, ... , valN)");
    }

    private static List<Arg> simpleArgs(final String... argNames) {
        return Arrays.stream(argNames)
                .map(argName -> new Arg(argName,
                        Type.STRING,
                        false,
                        false,
                        0,
                        "my desc",
                        null,
                        null))
                .toList();
    }

    private static List<Arg> optionalArgs(final String... argNames) {
        return Arrays.stream(argNames)
                .map(argName -> new Arg(argName,
                        Type.STRING,
                        true,
                        false,
                        0,
                        "my desc",
                        null,
                        null))
                .toList();
    }

    @SafeVarargs
    private static List<Arg> combine(final List<Arg>... lists) {
        return Arrays.stream(lists)
                .flatMap(List::stream)
                .toList();
    }
}
