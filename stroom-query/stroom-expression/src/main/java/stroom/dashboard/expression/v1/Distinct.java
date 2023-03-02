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

package stroom.dashboard.expression.v1;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@SuppressWarnings("unused") // Used by FunctionFactory
@FunctionDef(
        name = Distinct.NAME,
        commonCategory = FunctionCategory.AGGREGATE,
        commonReturnType = ValString.class,
        signatures = {
                @FunctionSignature(
                        description = "Concatenates the distinct supplied values together with no delimiter.",
                        returnDescription = "The distinct values concatenated together into one string with no " +
                                "delimiter. Up to a maximum of " + Distinct.DEFAULT_LIMIT + " values.",
                        args = {
                                @FunctionArg(
                                        name = "values",
                                        description = "Grouped field or the result of another function.",
                                        argType = Val.class)
                        }),
                @FunctionSignature(
                        description = "Concatenates the distinct supplied values together with the supplied delimiter.",
                        returnDescription = "The distinct values concatenated together into one string using the " +
                                "supplied delimiter. Up to a maximum of " + Distinct.DEFAULT_LIMIT + " values.",
                        args = {
                                @FunctionArg(
                                        name = "values",
                                        description = "Grouped field or the result of another function.",
                                        argType = Val.class),
                                @FunctionArg(
                                        name = "delimiter",
                                        description = "The string to delimit each concatenated value with, e.g. ', '.",
                                        argType = Val.class)
                        }),
                @FunctionSignature(
                        description = "Concatenates up to limit of the distinct supplied values together with " +
                                "the supplied delimiter.",
                        returnDescription = "The distinct values concatenated together into one string using the " +
                                "supplied delimiter.",
                        args = {
                                @FunctionArg(
                                        name = "values",
                                        description = "Grouped field or the result of another function.",
                                        argType = Val.class),
                                @FunctionArg(
                                        name = "delimiter",
                                        description = "The string to delimit each concatenated value with, e.g. ', '.",
                                        argType = Val.class),
                                @FunctionArg(
                                        name = "limit",
                                        description = "The maximum number of distinct values to concatenate together.",
                                        argType = ValInteger.class),
                        })
        })
class Distinct extends AbstractFunction {

    static final String NAME = "distinct";
    static final int DEFAULT_LIMIT = 10;

    private String delimiter = "";

    private int limit = DEFAULT_LIMIT;

    private Function function;

    public Distinct(final String name) {
        super(name, 1, 3);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        if (params.length >= 2) {
            delimiter = ParamParseUtil.parseStringParam(params, 1, name);
        }
        if (params.length >= 3) {
            limit = ParamParseUtil.parseIntParam(params, 2, name, true);
        }

        final Param param = params[0];
        if (param instanceof Function) {
            function = (Function) param;
        } else {
            function = new StaticValueFunction((Val) param);
        }
    }

    @Override
    public Generator createGenerator() {
        final Generator childGenerator = function.createGenerator();
        return new Gen(childGenerator, delimiter, limit);
    }

    @Override
    public boolean isAggregate() {
        return true;
    }

    @Override
    public boolean hasAggregate() {
        return isAggregate();
    }

    private static class Gen extends AbstractSingleChildGenerator {

        private final String delimiter;
        private final int limit;
        // The distinct input values in input order
        private final List<String> orderedDistinctValues;
        // The distinct input values
        private final Set<String> distinctValues;

        Gen(final Generator childGenerator, final String delimiter, final int limit) {
            super(childGenerator);
            this.delimiter = delimiter;
            this.limit = limit;
            this.distinctValues = new HashSet<>(limit);
            this.orderedDistinctValues = new ArrayList<>(limit);
        }

        @Override
        public void set(final Values values) {
            childGenerator.set(values);

            if (distinctValues.size() < limit) {
                final Val val = childGenerator.eval(null);
                final String value = val.toString();
                addValueIfDistinct(value);
            }
        }

        private void addValueIfDistinct(final String value) {
            if (value != null) {
                final boolean isDistinct = distinctValues.add(value);
                if (isDistinct) {
                    orderedDistinctValues.add(value);
                }
            }
        }

        @Override
        public Val eval(final Supplier<ChildData> childDataSupplier) {
            return ValString.create(String.join(delimiter, orderedDistinctValues));
        }

        @Override
        public void merge(final Generator generator) {
            final Gen gen = (Gen) generator;
            for (final String value : gen.orderedDistinctValues) {
                if (distinctValues.size() < limit) {
                    addValueIfDistinct(value);
                }
            }
            super.merge(generator);
        }

        @Override
        public void read(final Input input) {
            distinctValues.clear();
            orderedDistinctValues.clear();
            final int length = input.readInt();
            for (int i = 0; i < length; i++) {
                addValueIfDistinct(input.readString());
            }
        }

        @Override
        public void write(final Output output) {
            output.writeInt(orderedDistinctValues.size());
            for (final String string : orderedDistinctValues) {
                output.writeString(string);
            }
        }
    }
}
