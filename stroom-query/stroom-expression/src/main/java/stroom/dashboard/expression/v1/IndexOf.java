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

package stroom.dashboard.expression.v1;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.io.Serializable;
import java.text.ParseException;

class IndexOf extends AbstractFunction implements Serializable {
    static final String NAME = "indexOf";
    private static final long serialVersionUID = -305845496003936297L;
    private Function stringFunction;

    private Generator gen;
    private Function function;
    private boolean hasAggregate;

    public IndexOf(final String name) {
        super(name, 2, 2);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        stringFunction = ParamParseUtil.parseStringFunctionParam(params, 1, name);

        final Param param = params[0];
        if (param instanceof Function) {
            function = (Function) param;
            hasAggregate = function.hasAggregate();

        } else {
            function = new StaticValueFunction((Val) param);

            // Optimise replacement of static input in case user does something stupid.
            if (stringFunction instanceof StaticValueFunction) {
                final String string = stringFunction.createGenerator().eval().toString();
                if (string != null) {
                    final String value = param.toString();
                    final int index = value.indexOf(string);
                    if (index < 0) {
                        gen = new StaticValueFunction(ValNull.INSTANCE).createGenerator();
                    } else {
                        gen = new StaticValueFunction(ValInteger.create(index)).createGenerator();
                    }
                } else {
                    gen = new StaticValueFunction(ValNull.INSTANCE).createGenerator();
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
        return new Gen(childGenerator, stringFunction.createGenerator());
    }

    @Override
    public boolean hasAggregate() {
        return hasAggregate;
    }

    private static final class Gen extends AbstractSingleChildGenerator {
        private static final long serialVersionUID = 8153777070911899616L;

        private final Generator stringGenerator;

        Gen(final Generator childGenerator, final Generator stringGenerator) {
            super(childGenerator);
            this.stringGenerator = stringGenerator;
        }

        @Override
        public void set(final Val[] values) {
            childGenerator.set(values);
            stringGenerator.set(values);
        }

        @Override
        public Val eval() {
            final Val val = childGenerator.eval();
            if (!val.type().isValue()) {
                return ValErr.wrap(val);
            }
            final String value = val.toString();

            final Val strVal = stringGenerator.eval();
            if (!strVal.type().isValue()) {
                return ValErr.wrap(strVal);
            }
            final String str = strVal.toString();

            final int index = value.indexOf(str);
            if (index >= 0) {
                return ValInteger.create(index);
            }

            return ValNull.INSTANCE;
        }

        @Override
        public void read(final Input input) {
            super.read(input);
            stringGenerator.read(input);
        }

        @Override
        public void write(final Output output) {
            super.write(output);
            stringGenerator.write(output);
        }
    }
}
