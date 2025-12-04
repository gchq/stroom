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

import stroom.query.language.functions.ref.StoredValues;

import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = Concat.NAME,
        commonCategory = FunctionCategory.STRING,
        commonReturnType = ValString.class,
        commonReturnDescription = "A string containing all the arguments concatenated together.",
        signatures = @FunctionSignature(
                description = "Appends all the arguments end to end in a single string.",
                args = {
                        @FunctionArg(
                                name = "arg",
                                description = "Field, the result of another function or a constant.",
                                argType = ValString.class,
                                isVarargs = true,
                                minVarargsCount = 2)
                }))
class Concat extends AbstractManyChildFunction {

    static final String NAME = "concat";

    public Concat(final String name) {
        super(name, 1, Integer.MAX_VALUE);
    }

    @Override
    protected Generator createGenerator(final Generator[] childGenerators) {
        return new Gen(childGenerators);
    }

    private static final class Gen extends AbstractManyChildGenerator {

        Gen(final Generator[] childGenerators) {
            super(childGenerators);
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final StringBuilder sb = new StringBuilder();
            for (final Generator gen : childGenerators) {
                final Val val = gen.eval(storedValues, childDataSupplier);
                if (val.type().isError()) {
                    return val;
                }
                final String string = val.toString();
                if (string != null) {
                    sb.append(string);
                }
            }
            return ValString.create(sb.toString());
        }
    }
}
