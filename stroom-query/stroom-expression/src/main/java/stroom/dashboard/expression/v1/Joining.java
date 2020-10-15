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

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

class Joining extends AbstractFunction {
    static final String NAME = "joining";

    private String delimiter = "";
    private int limit = 10;

    private Function function;

    public Joining(final String name) {
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
        private static final long serialVersionUID = 8153777070911899616L;

        private final String delimiter;
        private final int limit;
        private final List<String> list = new ArrayList<>();

        Gen(final Generator childGenerator, final String delimiter, final int limit) {
            super(childGenerator);
            this.delimiter = delimiter;
            this.limit = limit;
        }

        @Override
        public void set(final Val[] values) {
            childGenerator.set(values);

            if (list.size() < limit) {
                final Val val = childGenerator.eval();
                final String value = val.toString();
                if (value != null) {
                    list.add(value);
                }
            }
        }

        @Override
        public Val eval() {
            return ValString.create(String.join(delimiter, list));
        }

        @Override
        public void merge(final Generator generator) {
            final Gen gen = (Gen) generator;
            for (final String value : gen.list) {
                if (list.size() < limit) {
                    list.add(value);
                }
            }
            super.merge(generator);
        }
    }
}
