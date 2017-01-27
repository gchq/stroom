/*
 * Copyright 2016 Crown Copyright
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

public class UpperCase extends AbstractFunction implements Serializable {
    public static final String NAME = "upperCase";
    private static final long serialVersionUID = -305845496003936297L;
    private Generator gen;
    private Function function = null;
    private boolean hasAggregate;

    public UpperCase(final String name) {
        super(name, 1, 1);
    }

    @Override
    public void setParams(final Object[] params) throws ParseException {
        super.setParams(params);

        final Object param = params[0];
        if (param instanceof Function) {
            function = (Function) param;
            hasAggregate = function.hasAggregate();
        } else {
            // Optimise replacement of static input in case user does something stupid.
            gen = new StaticValueFunction(param.toString().toUpperCase()).createGenerator();
            hasAggregate = false;
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

    private static class Gen extends AbstractSingleChildGenerator {
        private static final long serialVersionUID = 8153777070911899616L;


        public Gen(final Generator childGenerator) {
            super(childGenerator);

        }

        @Override
        public void set(final String[] values) {
            childGenerator.set(values);
        }

        @Override
        public Object eval() {
            final Object val = childGenerator.eval();
            if (val != null) {
                return TypeConverter.getString(val).toUpperCase();
            }

            return null;
        }
    }
}
