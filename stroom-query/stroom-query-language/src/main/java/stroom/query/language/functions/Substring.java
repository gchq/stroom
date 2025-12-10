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
        name = Substring.NAME,
        commonCategory = FunctionCategory.STRING,
        commonReturnType = ValString.class,
        commonReturnDescription = "The requested sub-string.",
        signatures = @FunctionSignature(
                description = "Extract a sub-string based on the start/end index values.",
                args = {
                        @FunctionArg(
                                name = "input",
                                description = "The input string to extract a sub-string from.",
                                argType = ValString.class),
                        @FunctionArg(
                                name = "startIndex",
                                description = "The index of the start of the sub-string range (zero based, inclusive).",
                                argType = ValInteger.class),
                        @FunctionArg(
                                name = "endIndex",
                                description = "The index of the end of the sub-string range (zero based, exclusive).",
                                argType = ValInteger.class),
                }))
class Substring extends AbstractFunction {

    static final String NAME = "substring";
    private Function startFunction;
    private Function endFunction;

    private Generator gen;
    private Function function;
    private boolean hasAggregate;

    public Substring(final String name) {
        super(name, 3, 3);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        startFunction = parsePosParam(params[1], "second");
        endFunction = parsePosParam(params[2], "third");

        final Param param = params[0];
        if (param instanceof Function) {
            function = (Function) param;
            hasAggregate = function.hasAggregate();

        } else {
            function = new StaticValueFunction((Val) param);

            // Optimise replacement of static input in case user does something stupid.
            if (startFunction instanceof StaticValueFunction && endFunction instanceof StaticValueFunction) {
                final String value = param.toString();
                final Double startPos = ((StaticValueFunction) startFunction).getValue().toDouble();
                final Double endPos = ((StaticValueFunction) endFunction).getValue().toDouble();

                if (value == null || startPos == null || endPos == null) {
                    gen = new StaticValueFunction(ValString.EMPTY).createGenerator();
                } else {
                    int start = startPos.intValue();
                    final int end = endPos.intValue();

                    if (start < 0) {
                        start = 0;
                    }

                    if (end < 0 || end < start || start >= value.length()) {
                        gen = new StaticValueFunction(ValString.EMPTY).createGenerator();
                    } else if (end >= value.length()) {
                        gen = new StaticValueFunction(ValString.create(value.substring(start))).createGenerator();
                    } else {
                        gen = new StaticValueFunction(ValString.create(value.substring(start, end))).createGenerator();
                    }
                }
            }
        }
    }

    private Function parsePosParam(final Param param, final String paramPos) throws ParseException {
        final Function function;
        if (param instanceof Function) {
            function = (Function) param;
        } else {
            final Integer pos = ((Val) param).toInteger();
            if (pos == null) {
                throw new ParseException("Number expected as " + paramPos + " argument of '" + name + "' function", 0);
            }
            function = new StaticValueFunction(ValInteger.create(pos));
        }
        return function;
    }

    @Override
    public Generator createGenerator() {
        if (gen != null) {
            return gen;
        }

        final Generator childGenerator = function.createGenerator();
        return new Gen(childGenerator, startFunction.createGenerator(), endFunction.createGenerator());
    }

    @Override
    public boolean hasAggregate() {
        return hasAggregate;
    }

    @Override
    public boolean requiresChildData() {
        boolean requiresChildData = super.requiresChildData();
        if (startFunction != null && !requiresChildData) {
            requiresChildData = startFunction.requiresChildData();
        }
        if (endFunction != null && !requiresChildData) {
            requiresChildData = endFunction.requiresChildData();
        }
        if (function != null && !requiresChildData) {
            requiresChildData = function.requiresChildData();
        }
        return requiresChildData;
    }

    private static final class Gen extends AbstractSingleChildGenerator {

        private final Generator startPosGenerator;
        private final Generator endPosGenerator;

        Gen(final Generator childGenerator, final Generator startPosGenerator, final Generator endPosGenerator) {
            super(childGenerator);
            this.startPosGenerator = startPosGenerator;
            this.endPosGenerator = endPosGenerator;
        }

        @Override
        public void set(final Val[] values, final StoredValues storedValues) {
            childGenerator.set(values, storedValues);
            startPosGenerator.set(values, storedValues);
            endPosGenerator.set(values, storedValues);
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final Val val = childGenerator.eval(storedValues, childDataSupplier);
            if (!val.type().isValue()) {
                return ValErr.wrap(val);
            }
            final Val startVal = startPosGenerator.eval(storedValues, childDataSupplier);
            if (!startVal.type().isValue()) {
                return ValErr.wrap(startVal);
            }
            final Val endVal = endPosGenerator.eval(storedValues, childDataSupplier);
            if (!endVal.type().isValue()) {
                return ValErr.wrap(endVal);
            }

            final String value = val.toString();
            final Integer startPos = startVal.toInteger();
            final Integer endPos = endVal.toInteger();
            if (startPos == null || endPos == null) {
                return ValErr.INSTANCE;
            }

            final int start = startPos;
            final int end = endPos;

            if (start < 0 || end < start || start >= value.length()) {
                return ValString.EMPTY;
            }

            if (end >= value.length()) {
                return ValString.create(value.substring(start));
            }
            return ValString.create(value.substring(start, end));
        }
    }
}
