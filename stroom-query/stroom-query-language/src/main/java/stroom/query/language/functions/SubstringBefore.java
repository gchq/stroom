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
        name = SubstringBefore.NAME,
        commonCategory = FunctionCategory.STRING,
        commonReturnType = ValString.class,
        commonReturnDescription = "The requested sub-string.",
        signatures = @FunctionSignature(
                description = "Extract a sub-string from input before the first occurrence of delimiter. " +
                        "e.g. substringBefore('key=>value', '=>') returns 'key'.",
                args = {
                        @FunctionArg(
                                name = "input",
                                description = "The input string to extract a sub-string from.",
                                argType = ValString.class),
                        @FunctionArg(
                                name = "delimiter",
                                description = "The string to find in input",
                                argType = ValString.class),
                }))
class SubstringBefore extends AbstractFunction {

    static final String NAME = "substringBefore";
    private Function beforeFunction;

    private Generator gen;
    private Function function;
    private boolean hasAggregate;

    public SubstringBefore(final String name) {
        super(name, 2, 2);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        beforeFunction = ParamParseUtil.parseStringFunctionParam(params, 1, name);

        final Param param = params[0];
        if (param instanceof Function) {
            function = (Function) param;
            hasAggregate = function.hasAggregate();

        } else {
            function = new StaticValueFunction((Val) param);

            // Optimise replacement of static input in case user does something stupid.
            if (beforeFunction instanceof StaticValueFunction) {
                final String before = ((StaticValueFunction) beforeFunction).getValue().toString();
                if (before != null) {
                    final String value = param.toString();
                    final int index = value.indexOf(before);

                    if (index < 0) {
                        gen = new StaticValueFunction(ValString.EMPTY).createGenerator();
                    } else {
                        gen = new StaticValueFunction(ValString.create(value.substring(0, index))).createGenerator();
                    }
                } else {
                    gen = new StaticValueFunction(ValString.EMPTY).createGenerator();
                }
            }
        }
    }

    @Override
    public Generator createGenerator() {
        if (gen != null) {
            return gen;
        }

        final Generator childGenerator = function.createGenerator();
        return new Gen(childGenerator, beforeFunction.createGenerator());
    }

    @Override
    public boolean hasAggregate() {
        return hasAggregate;
    }

    @Override
    public boolean requiresChildData() {
        boolean requiresChildData = super.requiresChildData();
        if (beforeFunction != null && !requiresChildData) {
            requiresChildData = beforeFunction.requiresChildData();
        }
        if (function != null && !requiresChildData) {
            requiresChildData = function.requiresChildData();
        }
        return requiresChildData;
    }

    private static final class Gen extends AbstractSingleChildGenerator {

        private final Generator stringGenerator;

        Gen(final Generator childGenerator, final Generator stringGenerator) {
            super(childGenerator);
            this.stringGenerator = stringGenerator;
        }

        @Override
        public void set(final Val[] values, final StoredValues storedValues) {
            childGenerator.set(values, storedValues);
            stringGenerator.set(values, storedValues);
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final Val val = childGenerator.eval(storedValues, childDataSupplier);
            if (!val.type().isValue()) {
                return ValErr.wrap(val);
            }
            final String value = val.toString();

            final Val strVal = stringGenerator.eval(storedValues, childDataSupplier);
            if (!strVal.type().isValue()) {
                return ValErr.wrap(strVal);
            }
            final String str = strVal.toString();

            final int index = value.indexOf(str);
            if (index < 0) {
                return ValString.EMPTY;
            }

            return ValString.create(value.substring(0, index));
        }
    }
}
