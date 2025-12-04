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

import java.text.ParseException;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = Or.NAME,
        commonCategory = FunctionCategory.LOGIC,
        commonReturnType = ValBoolean.class,
        commonReturnDescription = "If one or more of the supplied arguments evaluate to true then return true, " +
                "else false.",
        signatures = @FunctionSignature(
                description = "Logical 'or' operator.",
                args = {
                        @FunctionArg(
                                name = "boolean1",
                                description = "First boolean argument.",
                                argType = ValBoolean.class),
                        @FunctionArg(
                                name = "booleanN",
                                description = "Additional boolean arguments.",
                                argType = ValBoolean.class)}))
class Or extends AbstractManyChildFunction {

    static final String NAME = "or";

    public Or(final String name) {
        super(name, 1, Integer.MAX_VALUE);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);
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
            try {
                for (final Generator generator : childGenerators) {
                    if (generator != null) {
                        final Val val = generator.eval(storedValues, childDataSupplier);
                        if (val != null && val.type().isValue() && val.toBoolean() != null && val.toBoolean()) {
                            return ValBoolean.TRUE;
                        }
                    }
                }
                return ValBoolean.FALSE;
            } catch (final RuntimeException e) {
                return ValErr.create(e.getMessage());
            }
        }
    }
}
