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


@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = Exclude.NAME,
        commonCategory = FunctionCategory.STRING,
        commonReturnType = ValString.class,
        commonReturnDescription = "Null if the input matches one of the patterns, otherwise the input value.",
        signatures = @FunctionSignature(
                description = "If the supplied string matches one of the supplied regex patterns then return " +
                        "null, otherwise return the supplied string.",
                args = {
                        @FunctionArg(
                                name = "input",
                                description = "The input value to test against.",
                                argType = ValString.class),
                        @FunctionArg(
                                name = "pattern",
                                description = "The regex pattern to test against the input string.",
                                argType = ValString.class,
                                isVarargs = true,
                                minVarargsCount = 1)}))
class Exclude extends AbstractIncludeExclude {

    static final String NAME = "exclude";

    public Exclude(final String name) {
        super(name);
    }

    @Override
    boolean inverse() {
        return true;
    }

    @Override
    protected Generator createGenerator(final Generator[] childGenerators) {
        return new Gen(childGenerators);
    }

    private static final class Gen extends AbstractGen {

        Gen(final Generator[] childGenerators) {
            super(childGenerators);
        }

        @Override
        boolean inverse() {
            return true;
        }
    }
}
