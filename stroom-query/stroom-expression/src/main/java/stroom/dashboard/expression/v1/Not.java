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

import java.io.Serializable;
import java.text.ParseException;

class Not extends AbstractFunction implements Serializable {
    static final String NAME = "not";
    private static final long serialVersionUID = -305845496003936297L;
    private Generator gen;
    private Function function;
    private boolean hasAggregate;

    public Not(final String name) {
        super(name, 1, 1);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        final Param param = params[0];
        if (param instanceof Function) {
            function = (Function) param;
            hasAggregate = function.hasAggregate();
        } else {
            final Boolean condition = ((Val) params[0]).toBoolean();
            if (condition == null) {
                throw new ParseException("Expecting a condition for first argument of '" + name + "' function", 0);
            }
            // Static computation.
            gen = new StaticValueFunction(ValBoolean.create(!condition)).createGenerator();
        }
    }

    @Override
    public Generator createGenerator() {
        if (gen != null) {
            return gen;
        }

        final Generator childGenerator = function.createGenerator();
        return new Gen(childGenerator);
    }

    @Override
    public boolean hasAggregate() {
        return hasAggregate;
    }

    private static final class Gen extends AbstractSingleChildGenerator {
        private static final long serialVersionUID = 8153777070911899616L;

        Gen(final Generator childGenerator) {
            super(childGenerator);
        }

        @Override
        public void set(final Val[] values) {
            childGenerator.set(values);
        }

        @Override
        public Val eval() {
            final Val val = childGenerator.eval();
            if (!val.type().isValue()) {
                return val;
            }

            try {
                final Boolean condition = val.toBoolean();
                if (condition == null) {
                    return ValErr.create("Expecting a condition");
                }
                return ValBoolean.create(!condition);
            } catch (final RuntimeException e) {
                return ValErr.create(e.getMessage());
            }
        }
    }
}
