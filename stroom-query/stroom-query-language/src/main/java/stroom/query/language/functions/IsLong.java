/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.language.functions;


@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = IsLong.NAME,
        commonCategory = FunctionCategory.TYPE_CHECKING,
        commonReturnType = ValBoolean.class,
        commonReturnDescription = "True if value is a long.",
        signatures = @FunctionSignature(
                description = "Checks if the passed value has a long data type.",
                args = {
                        @FunctionArg(
                                name = "value",
                                description = "Field, the result of another function or a constant.",
                                argType = Val.class)
                }))
class IsLong extends AbstractIsFunction {

    static final String NAME = "isLong";
    private static final LongTest TEST = new LongTest();

    public IsLong(final String name) {
        super(name);
    }

    @Override
    Test getTest() {
        return TEST;
    }

    private static class LongTest implements Test {

        @Override
        public Val test(final Val val) {
            return ValBoolean.create(val instanceof ValLong);
        }
    }
}
