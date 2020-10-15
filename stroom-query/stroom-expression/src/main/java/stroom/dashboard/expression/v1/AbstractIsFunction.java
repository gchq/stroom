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

abstract class AbstractIsFunction extends AbstractFunction implements Serializable {
    private static final long serialVersionUID = -305145496413936297L;
    private Generator gen;
    private Function function;
    private boolean hasAggregate;

    AbstractIsFunction(final String name) {
        super(name, 1, 1);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        final Param param = params[0];
        if (param instanceof Function) {
            function = (Function) param;
            hasAggregate = function.hasAggregate();
        } else if (param instanceof Val) {
            // Static computation.
            gen = new StaticValueFunction(getTest().test((Val) param)).createGenerator();
        } else {
            throw new RuntimeException("Unexpected type [" + param.getClass().getSimpleName() + "]");
        }
    }

    @Override
    public Generator createGenerator() {
        if (gen != null) {
            return gen;
        }

        final Generator childGenerator = function.createGenerator();
        return new Gen(childGenerator, getTest());
    }

    abstract Test getTest();

    @Override
    public boolean hasAggregate() {
        return hasAggregate;
    }

    interface Test extends Serializable {
        Val test(Val val);
    }

    private static class Gen extends AbstractSingleChildGenerator {
        private static final long serialVersionUID = 8153777070911893616L;

        private final Test test;

        Gen(final Generator childGenerator, final Test test) {
            super(childGenerator);
            this.test = test;
        }

        @Override
        public void set(final Val[] values) {
            childGenerator.set(values);
        }

        @Override
        public Val eval() {
            return test.test(childGenerator.eval());
        }
    }
}
