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

import java.io.Serializable;
import java.text.ParseException;

class Substring extends AbstractFunction implements Serializable {
    static final String NAME = "substring";
    private static final long serialVersionUID = -305845496003936297L;
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
                final Double startPos = startFunction.createGenerator().eval().toDouble();
                final Double endPos = endFunction.createGenerator().eval().toDouble();

                if (value == null || startPos == null || endPos == null) {
                    gen = new StaticValueFunction(ValString.EMPTY).createGenerator();
                } else {
                    int start = startPos.intValue();
                    int end = endPos.intValue();

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
        Function function;
        if (param instanceof Function) {
            function = (Function) param;
        } else {
            Integer pos = ((Val) param).toInteger();
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

    private static final class Gen extends AbstractSingleChildGenerator {
        private static final long serialVersionUID = 8153777070911899616L;

        private final Generator startPosGenerator;
        private final Generator endPosGenerator;

        Gen(final Generator childGenerator, final Generator startPosGenerator, final Generator endPosGenerator) {
            super(childGenerator);
            this.startPosGenerator = startPosGenerator;
            this.endPosGenerator = endPosGenerator;
        }

        @Override
        public void set(final Val[] values) {
            childGenerator.set(values);
            startPosGenerator.set(values);
            endPosGenerator.set(values);
        }

        @Override
        public Val eval() {
            final Val val = childGenerator.eval();
            if (!val.type().isValue()) {
                return ValErr.wrap(val);
            }
            final Val startVal = startPosGenerator.eval();
            if (!startVal.type().isValue()) {
                return ValErr.wrap(startVal);
            }
            final Val endVal = endPosGenerator.eval();
            if (!endVal.type().isValue()) {
                return ValErr.wrap(endVal);
            }

            final String value = val.toString();
            final Integer startPos = startVal.toInteger();
            final Integer endPos = endVal.toInteger();
            if (startPos == null || endPos == null) {
                return ValErr.INSTANCE;
            }

            int start = startPos;
            int end = endPos;

            if (start < 0) {
                start = 0;
            }

            if (end < 0 || end < start || start >= value.length()) {
                return ValString.EMPTY;
            }

            if (end >= value.length()) {
                return ValString.create(value.substring(start));
            }
            return ValString.create(value.substring(start, end));
        }

        @Override
        public void read(final Input input) {
            super.read(input);
            startPosGenerator.read(input);
            endPosGenerator.read(input);
        }

        @Override
        public void write(final Output output) {
            super.write(output);
            startPosGenerator.write(output);
            endPosGenerator.write(output);
        }
    }
}
