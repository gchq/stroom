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

import java.text.ParseException;
import java.util.HashSet;
import java.util.Set;

public class CountUnique extends AbstractFunction {
    private static class Gen extends AbstractSingleChildGenerator {
        private static final long serialVersionUID = -6770724151493320673L;

        private final Set<Object> uniqueValues = new HashSet<>();

        public Gen(final Generator childGenerator) {
            super(childGenerator);
        }

        @Override
        public void set(final String[] values) {
            childGenerator.set(values);
            final Object value = childGenerator.eval();
            if (value != null) {
                uniqueValues.add(value);
            }
        }

        @Override
        public Object eval() {
            return (double) uniqueValues.size();
        }

        @Override
        public void merge(final Generator generator) {
            final Gen gen = (Gen) generator;
            uniqueValues.addAll(gen.uniqueValues);
            super.merge(generator);
        }
    }

    public static final String NAME = "countUnique";

    private Generator gen;
    private Function function = null;

    public CountUnique(final String name) {
        super(name, 1, 1);
    }

    @Override
    public void setParams(final Object[] params) throws ParseException {
        super.setParams(params);

        final Object param = params[0];
        if (param instanceof Function) {
            function = (Function) param;

            if (function.hasAggregate()) {
                throw new ParseException(name + " cannot be applied to aggregating function", 0);
            }

        } else {
            /*
             * Optimise replacement of static input in case user does something
             * stupid.
             */
            gen = new StaticValueFunction(1D).createGenerator();
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
    public boolean isAggregate() {
        return true;
    }

    @Override
    public boolean hasAggregate() {
        return isAggregate();
    }
}
