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
import stroom.query.language.functions.ref.StringListReference;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import java.text.ParseException;
import java.util.List;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = Joining.NAME,
        commonCategory = FunctionCategory.AGGREGATE,
        commonReturnType = ValString.class,
        signatures = {
                @FunctionSignature(
                        description = "Concatenates the supplied values together with no delimiter.",
                        returnDescription = "The values concatenated together into one string with no delimiter. " +
                                "Up to a maximum of " + Joining.DEFAULT_LIMIT + " values.",
                        args = {
                                @FunctionArg(
                                        name = "values",
                                        description = "Grouped field or the result of another function.",
                                        argType = Val.class)
                        }),
                @FunctionSignature(
                        description = "Concatenates the supplied values together with the supplied delimiter.",
                        returnDescription = "The values concatenated together into one string using the supplied " +
                                "delimiter. Up to a maximum of " + Joining.DEFAULT_LIMIT + " values.",
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
                        description = "Concatenates up to limit of the supplied values together with the supplied " +
                                "delimiter.",
                        returnDescription = "The values concatenated together into one string using the supplied " +
                                "delimiter.",
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
                                        description = "The maximum number of values to concatenate together.",
                                        argType = ValInteger.class),
                        })
        })
class Joining extends AbstractFunction implements AggregateFunction {

    static final String NAME = "joining";
    static final int DEFAULT_LIMIT = 10;

    private final ExpressionContext context;
    private String delimiter = "";

    private int limit = DEFAULT_LIMIT;

    private Function function;
    private StringListReference stringListReference;

    public Joining(final ExpressionContext context,
                   final String name) {
        super(name, 1, 3);
        this.context = context;
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
    public void addValueReferences(final ValueReferenceIndex valueReferenceIndex) {
        stringListReference = valueReferenceIndex.addStringList(name);
        super.addValueReferences(valueReferenceIndex);
    }

    @Override
    public Generator createGenerator() {
        final Generator childGenerator = function.createGenerator();
        return new Gen(childGenerator, delimiter, limit, context.getMaxStringLength(), stringListReference);
    }

    @Override
    public boolean isAggregate() {
        return true;
    }

    @Override
    public boolean hasAggregate() {
        return isAggregate();
    }

    @Override
    public boolean requiresChildData() {
        if (function != null) {
            return function.requiresChildData();
        }
        return super.requiresChildData();
    }

    private static class Gen extends AbstractSingleChildGenerator {

        private final String delimiter;
        private final int limit;
        private final int maxStringLength;
        private final StringListReference stringListReference;

        Gen(final Generator childGenerator,
            final String delimiter,
            final int limit,
            final int maxStringLength,
            final StringListReference stringListReference) {
            super(childGenerator);
            this.delimiter = delimiter;
            this.limit = limit;
            this.maxStringLength = maxStringLength;
            this.stringListReference = stringListReference;
        }

        @Override
        public void set(final Val[] values, final StoredValues storedValues) {
            childGenerator.set(values, storedValues);

            List<String> list = stringListReference.get(storedValues);
            if (list.size() < limit) {
                final Val val = childGenerator.eval(storedValues, null);
                final String value = val.toString();
                if (value != null) {
                    list.add(value);
                    list = trimList(list);
                    stringListReference.set(storedValues, list);
                }
            }
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final List<String> list = stringListReference.get(storedValues);
            final StringBuilder sb = new StringBuilder();
            for (final String s : list) {
                sb.append(s);
                sb.append(delimiter);
            }
            if (sb.length() > 0) {
                sb.setLength(sb.length() - delimiter.length());
            }
            return ValString.create(sb.toString());
        }

        @Override
        public void merge(final StoredValues existingValues, final StoredValues newValues) {
            List<String> list = stringListReference.get(existingValues);
            if (list.size() < limit) {
                final List<String> newList = stringListReference.get(newValues);
                if (newList.size() > 0) {
                    list.addAll(newList);
                    list = trimList(list);
                    stringListReference.set(existingValues, list);
                }
            }
            super.merge(existingValues, newValues);
        }

        private List<String> trimList(final List<String> list) {
            int trimSize = 0;

            int totalLength = 0;
            for (final String s : list) {
                totalLength += s.length();
                trimSize++;
                if (totalLength >= maxStringLength) {
                    break;
                }
            }

            trimSize = Math.min(trimSize, limit);
            if (list.size() > trimSize) {
                return list.subList(0, trimSize);
            } else {
                return list;
            }
        }
    }
}
