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

import stroom.query.language.functions.ref.StoredValues;
import stroom.util.shared.ModelStringUtil;

import java.text.ParseException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = FormatIECByteSize.NAME,
        commonCategory = FunctionCategory.VALUE,
        commonReturnType = ValString.class,
        signatures = {
                @FunctionSignature(
                        args = @FunctionArg(
                                name = "bytes",
                                argType = ValLong.class,
                                description = "The number of bytes"),
                        returnDescription = "A more human readable IEC representation of byte size.",
                        description = "Convert a number of bytes into a more human readable form."),
                @FunctionSignature(
                        args = {
                                @FunctionArg(
                                        name = "bytes",
                                        argType = ValLong.class,
                                        description = "The number of bytes"),
                                @FunctionArg(
                                        name = "omitTrailingZeros",
                                        argType = ValBoolean.class,
                                        defaultValue = "false",
                                        description = "Whether to omit trailing zeros (default false)")},
                        returnDescription = "A more human readable IEC representation of byte size.",
                        description = "Convert a number of bytes into a more human readable form."),
                @FunctionSignature(
                        args = {
                                @FunctionArg(
                                        name = "bytes",
                                        argType = ValLong.class,
                                        description = "The number of bytes"),
                                @FunctionArg(
                                        name = "omitTrailingZeros",
                                        argType = ValBoolean.class,
                                        defaultValue = "false",
                                        description = "Whether to omit trailing zeros (default false)"),
                                @FunctionArg(
                                        name = "significantFigures",
                                        argType = ValInteger.class,
                                        description = "The number of significant digits required, however if the " +
                                                      "number of integer digits is greater that will be used. " +
                                                      "This is to ensure we always show full precision for the " +
                                                      "integer part, " +
                                                      "e.g. output '1023B' when significantFigures is 3")},
                        returnDescription = "A more human readable IEC representation of byte size.",
                        description = "Convert a number of bytes into a more human readable form.")})
class FormatIECByteSize extends AbstractFunction {

    static final String NAME = "formatIECByteSize";
    private boolean omitTrailingZeros;
    private Integer significantFigures;

    private Generator gen;
    private Function function;
    private boolean hasAggregate;

    public FormatIECByteSize(final String name) {
        super(name, 1, 3);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        final DateTimeFormatter formatter = DateUtil.DEFAULT_FORMATTER;
        final ZoneId zoneId = ZoneOffset.UTC;

        if (params.length >= 2) {
            omitTrailingZeros = ParamParseUtil.parseBooleanParam(params, 1, name);
        }
        if (params.length >= 3) {
            significantFigures = ParamParseUtil.parseIntParam(params, 2, name, true);
        }

        final Param param = params[0];
        if (param instanceof Function) {
            function = (Function) param;
            hasAggregate = function.hasAggregate();

        } else {
            final Long bytes = ((Val) param).toLong();
            if (bytes == null) {
                throw new ParseException("Unable to convert first argument of '" + name + "' function to long", 0);
            }
            gen = new StaticValueFunction(ValLong.create(bytes)).createGenerator();
        }
    }

    @Override
    public Generator createGenerator() {
        if (gen != null) {
            return gen;
        }

        final Generator childGenerator = function.createGenerator();
        return new Gen(childGenerator, omitTrailingZeros, significantFigures);
    }

    @Override
    public boolean hasAggregate() {
        return hasAggregate;
    }

    @Override
    public boolean requiresChildData() {
        if (function != null) {
            return function.requiresChildData();
        }
        return super.requiresChildData();
    }


    // --------------------------------------------------------------------------------


    private static class Gen extends AbstractSingleChildGenerator {

        private final boolean omitTrailingZeros;
        private final Integer significantFigures;

        Gen(final Generator childGenerator, final boolean omitTrailingZeros, final Integer significantFigures) {
            super(childGenerator);
            this.omitTrailingZeros = omitTrailingZeros;
            this.significantFigures = significantFigures;
        }

        @Override
        public void set(final Val[] values, final StoredValues storedValues) {
            childGenerator.set(values, storedValues);
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final Val val = childGenerator.eval(storedValues, childDataSupplier);
            if (!val.type().isValue()) {
                return val;
            }
            final Long bytes = val.toLong();
            if (bytes == null) {
                return ValErr.create("Unable to convert argument to long");
            }

            try {
                if (significantFigures == null) {
                    return ValString.create(ModelStringUtil.formatIECByteSizeString(bytes, omitTrailingZeros));
                }

                return ValString.create(ModelStringUtil.formatIECByteSizeString(
                        bytes,
                        omitTrailingZeros,
                        significantFigures));
            } catch (final RuntimeException e) {
                return ValErr.create(e.getMessage());
            }
        }
    }
}
