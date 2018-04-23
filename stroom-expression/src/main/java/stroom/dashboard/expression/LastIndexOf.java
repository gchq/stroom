/*
 * Copyright 2018 Crown Copyright
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

package stroom.dashboard.expression;

import java.io.Serializable;
import java.text.ParseException;

public class LastIndexOf extends AbstractFunction implements Serializable {
    private static final long serialVersionUID = -305845496003936297L;

    public static final String NAME = "lastIndexOf";

    private Function stringFunction;

    private Generator gen;
    private Function function = null;
    private boolean hasAggregate;

    public LastIndexOf(final String name) {
        super(name, 2, 2);
    }

    @Override
    public void setParams(final Object[] params) throws ParseException {
        super.setParams(params);

        stringFunction = parseParam(params[1], "second");

        final Object param = params[0];
        if (param instanceof Function) {
            function = (Function) param;
            hasAggregate = function.hasAggregate();

        } else {
            function = new StaticValueFunction(param.toString());
            hasAggregate = false;

            // Optimise replacement of static input in case user does something stupid.
            if (stringFunction instanceof StaticValueFunction) {
                final String string = TypeConverter.getString(stringFunction.createGenerator().eval());
                if (string != null) {
                    final String value = param.toString();
                    final int index = value.lastIndexOf(string);
                    if (index < 0) {
                        gen = new StaticValueFunction(null).createGenerator();
                    } else {
                        gen = new StaticValueFunction(index).createGenerator();
                    }
                } else {
                    gen = new StaticValueFunction(null).createGenerator();
                }
            }
        }
    }

    private Function parseParam(final Object param, final String paramPos) throws ParseException {
        Function function;
        if (param instanceof Function) {
            function = (Function) param;
            if (function.hasAggregate()) {
                throw new ParseException("Non aggregate function expected as " + paramPos + " argument of '" + name + "' function", 0);
            }
        } else {
            final String string = TypeConverter.getString(param);
            if (string == null) {
                throw new ParseException("String expected as " + paramPos + " argument of '" + name + "' function", 0);
            }
            function = new StaticValueFunction(string);
        }
        return function;
    }

    @Override
    public Generator createGenerator() {
        if (gen != null) {
            return gen;
        }

        final Generator childGenerator = function.createGenerator();
        return new Gen(childGenerator, stringFunction.createGenerator());
    }

    @Override
    public boolean hasAggregate() {
        return hasAggregate;
    }

    private static class Gen extends AbstractSingleChildGenerator {
        private static final long serialVersionUID = 8153777070911899616L;

        private Generator stringGenerator;

        public Gen(final Generator childGenerator, final Generator stringGenerator) {
            super(childGenerator);
            this.stringGenerator = stringGenerator;
        }

        @Override
        public void set(final String[] values) {
            childGenerator.set(values);
            stringGenerator.set(values);
        }

        @Override
        public Object eval() {
            final Object val = childGenerator.eval();
            if (val != null) {
                final String string = TypeConverter.getString(stringGenerator.eval());
                if (string != null) {
                    final String value = TypeConverter.getString(val);
                    final int index = value.lastIndexOf(string);
                    if (index < 0) {
                        return null;
                    }
                    return index;
                }
            }

            return null;
        }
    }
}
